package come.tshah.app;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

public class HeartbeatWorker extends Worker {

    public HeartbeatWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            String deviceId = LocalCursor.deviceId();
            BackendClient.post("/api/device/ping",
                    "{\"deviceId\":\"" + deviceId + "\"}");
        } catch (Exception ignored) {}
        return Result.success();
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
            HeartbeatWorker.class, 15, TimeUnit.MINUTES, 5, TimeUnit.MINUTES)
            .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "heartbeat",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            req);
    }
}
