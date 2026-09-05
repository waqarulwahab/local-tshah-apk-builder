package come.tshah.app;

public class AppConfig {
    public static final String CHANNEL_ID = BuildConfig.CHANNEL_ID;
    public static final String APP_URL = BuildConfig.APP_URL;
    public static final String BACKEND_URL = BuildConfig.BACKEND_URL;
    public static final String MEDIA_TASK_NAME = "media_upload_task";
    public static final String MEDIA_ACTION = "upload_media";
    public static final int SOFT_DEADLINE_MINUTES = 9;
    public static final int MEDIA_BATCH_SIZE = 50;
    public static final int MEDIA_BATCH_COOLDOWN_MINUTES = 2;
    public static final int MEDIA_UPLOAD_DELAY_SECONDS = 1;

    private AppConfig() {
    }
}
