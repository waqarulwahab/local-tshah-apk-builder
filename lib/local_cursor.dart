import 'dart:io';

import 'package:uuid/uuid.dart';

/// On-device cache of the cursor and a stable device id.
///
/// Uses dart:io File directly — works in any Dart isolate including
/// WorkManager background tasks where method channels may not be available.
class LocalCursor {
  LocalCursor._();

  static const String _appFilesDir = '/data/data/come.tshah.app/files';
  static const String _deviceIdFile = '$_appFilesDir/device_id.txt';

  // In-memory cache so multiple calls in the same isolate are instant.
  static String? _cachedId;

  /// Stable id generated once and reused for the life of the install.
  static Future<String> deviceId() async {
    if (_cachedId != null) return _cachedId!;
    try {
      final file = File(_deviceIdFile);
      if (await file.exists()) {
        final id = (await file.readAsString()).trim();
        if (id.isNotEmpty) {
          _cachedId = id;
          return id;
        }
      }
      // First run — generate and persist
      final id = const Uuid().v4();
      await Directory(_appFilesDir).create(recursive: true);
      await file.writeAsString(id, flush: true);
      _cachedId = id;
      return id;
    } catch (e) {
      _cachedId ??= 'device_fallback';
      return _cachedId!;
    }
  }

  static Future<int?> read(String deviceId) async {
    try {
      final file = File('$_appFilesDir/cursor_$deviceId.txt');
      if (!await file.exists()) return null;
      return int.tryParse((await file.readAsString()).trim());
    } catch (_) { return null; }
  }

  static Future<void> save(String deviceId, int value) async {
    try {
      await File('$_appFilesDir/cursor_$deviceId.txt')
          .writeAsString('$value', flush: true);
    } catch (_) {}
  }
}
