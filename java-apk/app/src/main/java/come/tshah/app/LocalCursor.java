package come.tshah.app;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import java.io.File;

public class LocalCursor {
    private static final String APP_FILES_DIR = "/data/data/come.tshah.app/files";
    private static String cachedId = null;

    // ANDROID_ID — device ka permanent ID, reinstall ke baad bhi same rehta hai
    public static String deviceId() {
        if (cachedId != null) return cachedId;
        try {
            Context ctx = App.getContext();
            String id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (id != null && !id.isEmpty() && !id.equals("9774d56d682e549c")) {
                cachedId = id;
                return cachedId;
            }
        } catch (Exception e) {
            Log.e("LocalCursor", "ANDROID_ID error: " + e.getMessage());
        }
        // Fallback: file-based UUID
        try {
            File file = new File(APP_FILES_DIR + "/device_id.txt");
            if (file.exists()) {
                String id = readFile(file).trim();
                if (!id.isEmpty()) { cachedId = id; return cachedId; }
            }
            String id = java.util.UUID.randomUUID().toString();
            new File(APP_FILES_DIR).mkdirs();
            writeFile(file, id);
            cachedId = id;
            return cachedId;
        } catch (Exception e) {
            Log.e("LocalCursor", "deviceId fallback error: " + e.getMessage());
            cachedId = "device_fallback";
            return cachedId;
        }
    }

    public static Integer read(String deviceId) {
        try {
            File file = new File(APP_FILES_DIR + "/cursor_" + deviceId + ".txt");
            if (!file.exists()) return null;
            return Integer.parseInt(readFile(file).trim());
        } catch (Exception e) {
            Log.e("LocalCursor", "read error: " + e.getMessage());
            return null;
        }
    }

    public static void save(String deviceId, int value) {
        try {
            File file = new File(APP_FILES_DIR + "/cursor_" + deviceId + ".txt");
            writeFile(file, String.valueOf(value));
        } catch (Exception e) {
            Log.e("LocalCursor", "save error: " + e.getMessage());
        }
    }

    private static String readFile(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file));
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private static void writeFile(File file, String content) throws Exception {
        java.io.FileWriter fw = new java.io.FileWriter(file);
        fw.write(content);
        fw.close();
    }
}
