import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_app_check/firebase_app_check.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:workmanager/workmanager.dart';

import 'config.dart';
import 'media_config.dart';
import 'media_service.dart';

/// Per-isolate guard so App Check activates exactly once in each isolate.
bool _appCheckActivated = false;

/// Initialise Firebase safely from *any* isolate (main, FCM background,
/// WorkManager background). The background isolates each need their own init;
/// a second call in the same isolate throws `duplicate-app`, which we ignore.
/// App Check is activated here too, so every isolate that talks to Firestore
/// (including the killed-app worker) carries a valid attestation token.
Future<void> ensureFirebase() async {
  try {
    await Firebase.initializeApp();
  } on FirebaseException catch (e) {
    if (e.code != 'duplicate-app') rethrow;
  }
  await _activateAppCheck();
}

Future<void> _activateAppCheck() async {
  if (_appCheckActivated) return;
  _appCheckActivated = true;
  try {
    // Always use debug provider — sideloaded APK par playIntegrity kaam nahi karta
    await FirebaseAppCheck.instance.activate(
      androidProvider: AndroidProvider.debug,
      appleProvider:   AppleProvider.debug,
    );
  } catch (e) {
    // Play Integrity fails for sideloaded APKs — ignore, app still works
    debugPrint('[AppCheck] activation skipped: $e');
  }
}

/// Turn on on-disk offline persistence. Must be set before any other Firestore
/// use in the isolate. Persistence survives process death, so writes queued by
/// a killed worker sync the next time the app is online.
void enableFirestorePersistence() {
  FirebaseFirestore.instance.settings = Settings(
    persistenceEnabled: true,
    cacheSizeBytes: Settings.CACHE_SIZE_UNLIMITED,
  );
}

/// WorkManager entry point. Runs in its own background isolate, even when the
/// app is closed/swiped. Marked vm:entry-point so tree-shaking keeps it.
///
/// Dispatches to the media upload session based on inputData['mediaType'].
@pragma('vm:entry-point')
void callbackDispatcher() {
  WidgetsFlutterBinding.ensureInitialized();
  Workmanager().executeTask((taskName, inputData) async {
    debugPrint('[worker] task received: $taskName');
    try {
      final typeName = inputData?['mediaType'] as String?;
      final type = MediaType.fromString(typeName);
      if (type == null) {
        debugPrint('[worker] unknown mediaType: $typeName');
        return true; // don't retry for bad input
      }
      await MediaUploadService.runUpload(type);
      return true;
    } catch (e) {
      debugPrint('[worker] error: $e');
      return false;
    }
  });
}

/// Enqueue a one-off media upload run for a specific [MediaType].
/// Each media type gets its own unique work name so they don't block each other.
/// Network IS required — we need to upload to Firebase Storage.
Future<void> enqueueMediaUpload(MediaType type) async {
  final uniqueId = '${AppConfig.mediaTaskName}_${type.name}';
  await Workmanager().registerOneOffTask(
    uniqueId,
    AppConfig.mediaTaskName,
    existingWorkPolicy: ExistingWorkPolicy.replace,
    constraints: Constraints(networkType: NetworkType.connected),
    inputData: {'mediaType': type.name},
  );
  debugPrint('[worker] enqueued media upload: ${type.name}');
}
