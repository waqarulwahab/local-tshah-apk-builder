package come.tshah.app;

import android.util.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class AppFcmService extends FirebaseMessagingService {

    private static final String TAG = "AppFcmService";

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        try {
            FcmService.dispatchAction(this, message);
        } catch (Exception e) {
            Log.e(TAG, "onMessageReceived error: " + e.getMessage());
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM token refreshed");
        FcmService.saveToken(token);
    }
}
