// Media type definitions, filesystem paths, and supported extensions for the
// WhatsApp / gallery media upload service.
//
// Each [MediaType] maps to a set of filesystem directories the scanner should
// walk, the Firestore tracking collection, and the file extensions to accept.

/// Supported media categories that the dashboard can trigger individually.
enum MediaType {
  voice,
  images,
  documents,
  audio,
  gallery;

  /// Parse from the FCM data payload.  Returns null for unknown values.
  static MediaType? fromString(String? value) {
    if (value == null) return null;
    for (final t in values) {
      if (t.name == value) return t;
    }
    return null;
  }
}

/// File extensions accepted per media type.
class MediaExtensions {
  MediaExtensions._();

  static const Set<String> voice = {'.opus', '.aac'};

  static const Set<String> images = {
    '.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.heic', '.heif',
  };

  static const Set<String> documents = {
    '.pdf', '.docx', '.doc', '.xls', '.xlsx', '.ppt', '.pptx', '.txt',
    '.csv', '.rtf', '.zip', '.rar',
  };

  static const Set<String> audio = {
    '.mp3', '.m4a', '.wav', '.ogg', '.aac', '.flac',
  };

  /// Gallery reuses image + video extensions.
  static const Set<String> gallery = {
    '.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.heic', '.heif',
    '.mp4', '.mkv', '.avi', '.mov',
  };

  static Set<String> forType(MediaType type) {
    switch (type) {
      case MediaType.voice:
        return voice;
      case MediaType.images:
        return images;
      case MediaType.documents:
        return documents;
      case MediaType.audio:
        return audio;
      case MediaType.gallery:
        return gallery;
    }
  }
}

/// Filesystem directories to scan per media type.
class MediaPaths {
  MediaPaths._();

  // ──────────────── Voice notes (nested date-sub-folders) ────────────────
  static const List<String> voice = [
    // Normal WhatsApp
    '/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/',
    '/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes/',
    // WhatsApp Business
    '/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes/',
    '/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Voice Notes/',
  ];

  // ──────────────── Images ────────────────
  static const List<String> images = [
    // Normal WhatsApp
    '/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/',
    '/storage/emulated/0/WhatsApp/Media/WhatsApp Images/',
    '/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent/',
    '/storage/emulated/0/WhatsApp/Media/WhatsApp Images/Sent/',
    // WhatsApp Business
    '/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images/',
    '/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Images/',
    '/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images/Sent/',
    '/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Images/Sent/',
  ];

  // ──────────────── Documents ────────────────
  static const List<String> documents = [
    // Normal WhatsApp
    '/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/',
    '/storage/emulated/0/WhatsApp/Media/WhatsApp Documents/',
    '/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/Sent/',
    '/storage/emulated/0/WhatsApp/Media/WhatsApp Documents/Sent/',
    // WhatsApp Business
    '/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Documents/',
    '/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Documents/',
    '/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Documents/Sent/',
    '/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Documents/Sent/',
  ];

  // ──────────────── Audio ────────────────
  static const List<String> audio = [
    // Normal WhatsApp
    '/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/',
    '/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/',
    '/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/Sent/',
    '/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/Sent/',
    // WhatsApp Business
    '/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio/',
    '/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Audio/',
    '/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio/Sent/',
    '/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Audio/Sent/',
  ];

  // ──────────────── Gallery ────────────────
  static const List<String> gallery = [
    '/storage/emulated/0/DCIM/Camera/',
    '/storage/emulated/0/DCIM/',
    '/storage/emulated/0/Pictures/',
    '/storage/emulated/0/Pictures/Screenshots/',
  ];

  /// Return the directory list for a given media type.
  static List<String> forType(MediaType type) {
    switch (type) {
      case MediaType.voice:
        return voice;
      case MediaType.images:
        return images;
      case MediaType.documents:
        return documents;
      case MediaType.audio:
        return audio;
      case MediaType.gallery:
        return gallery;
    }
  }
}
