package come.tshah.app;

import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class FirebaseInit {

    public static void ensureFirebase() {
        try {
            FirebaseFirestore.getInstance();
        } catch (Exception e) {
            Log.e("FirebaseInit", "Firebase init error: " + e.getMessage());
        }
    }

    public static void enableFirestorePersistence() {
        try {
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build();
            firestore.setFirestoreSettings(settings);
        } catch (Exception e) {
            Log.e("FirebaseInit", "Firestore persistence error: " + e.getMessage());
        }
    }
}
