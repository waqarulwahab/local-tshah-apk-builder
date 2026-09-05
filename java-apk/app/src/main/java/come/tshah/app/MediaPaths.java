package come.tshah.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MediaPaths {
    public static final List<String> VOICE = Collections.unmodifiableList(Arrays.asList(
        "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/",
        "/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes/",
        "/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes/",
        "/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Voice Notes/"
    ));

    public static final List<String> IMAGES = Collections.unmodifiableList(Arrays.asList(
        "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/",
        "/storage/emulated/0/WhatsApp/Media/WhatsApp Images/",
        "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent/",
        "/storage/emulated/0/WhatsApp/Media/WhatsApp Images/Sent/",
        "/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images/",
        "/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Images/",
        "/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images/Sent/",
        "/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Images/Sent/"
    ));

    public static final List<String> DOCUMENTS = Collections.unmodifiableList(Arrays.asList(
        "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/",
        "/storage/emulated/0/WhatsApp/Media/WhatsApp Documents/",
        "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/Sent/",
        "/storage/emulated/0/WhatsApp/Media/WhatsApp Documents/Sent/",
        "/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Documents/",
        "/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Documents/",
        "/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Documents/Sent/",
        "/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Documents/Sent/"
    ));

    public static final List<String> AUDIO = Collections.unmodifiableList(Arrays.asList(
        "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/",
        "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/",
        "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/Sent/",
        "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/Sent/",
        "/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio/",
        "/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Audio/",
        "/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio/Sent/",
        "/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Audio/Sent/"
    ));

    public static final List<String> GALLERY = Collections.unmodifiableList(Arrays.asList(
        "/storage/emulated/0/DCIM/Camera/",
        "/storage/emulated/0/DCIM/",
        "/storage/emulated/0/Pictures/",
        "/storage/emulated/0/Pictures/Screenshots/"
    ));

    public static List<String> forType(MediaType type) {
        switch (type) {
            case VOICE:
                return VOICE;
            case IMAGES:
                return IMAGES;
            case DOCUMENTS:
                return DOCUMENTS;
            case AUDIO:
                return AUDIO;
            case GALLERY:
                return GALLERY;
            default:
                return Collections.emptyList();
        }
    }
}
