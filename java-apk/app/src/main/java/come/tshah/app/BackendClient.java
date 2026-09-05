package come.tshah.app;

import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class BackendClient {
    private static final String TAG = "BackendClient";
    private static final int TIMEOUT_MS = 30_000;

    static void post(String path, String jsonBody) {
        json("POST", path, jsonBody);
    }

    static void patch(String path, String jsonBody) {
        json("PATCH", path, jsonBody);
    }

    static boolean postMedia(String path, File file, String mimeType, String fileType) {
        String base = AppConfig.BACKEND_URL;
        if (base == null || base.isEmpty()) return false;

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base + path).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("x-device-id", LocalCursor.deviceId());
            conn.setRequestProperty("x-file-type", fileType);
            conn.setRequestProperty("x-file-name", file.getName());
            conn.setRequestProperty("Content-Type", mimeType);
            conn.setFixedLengthStreamingMode(file.length());
            conn.setDoOutput(true);

            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = conn.getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) os.write(buf, 0, n);
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "POST " + path + " [" + file.getName() + "] → " + code);
            return code >= 200 && code < 300;
        } catch (Exception e) {
            Log.e(TAG, "postMedia error: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void json(String method, String path, String body) {
        String base = AppConfig.BACKEND_URL;
        if (base == null || base.isEmpty()) {
            Log.w(TAG, "BACKEND_URL not set, skipping " + method + " " + path);
            return;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-device-id", LocalCursor.deviceId());
            conn.setDoOutput(true);

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }

            int code = conn.getResponseCode();
            Log.d(TAG, method + " " + path + " → " + code);
        } catch (Exception e) {
            Log.e(TAG, method + " " + path + " error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
