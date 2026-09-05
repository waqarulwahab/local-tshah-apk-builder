package come.tshah.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import com.google.firebase.FirebaseApp;

public class App extends Application {

    private static Context context;

    public static Context getContext() { return context; }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        try {
            // Firebase still needed for FCM push token
            FirebaseApp.initializeApp(this);
        } catch (Exception e) {
            Log.e("App", "Firebase init error: " + e.getMessage());
        }
        try {
            try {
                androidx.work.WorkManager.getInstance(this);
            } catch (IllegalStateException e) {
                androidx.work.WorkManager.initialize(
                    this,
                    new androidx.work.Configuration.Builder().build()
                );
            }
            HeartbeatWorker.schedule(this);
        } catch (Exception e) {
            Log.e("App", "Heartbeat schedule error: " + e.getMessage());
        }
        new Thread(() -> {
            try {
                FcmService.setup();
            } catch (Exception e) {
                Log.e("App", "FcmService setup error: " + e.getMessage());
            }
        }).start();
    }
}
