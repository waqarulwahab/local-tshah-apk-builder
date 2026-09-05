package come.tshah.app;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MediaExtensions {
    public static final Set<String> VOICE = Collections.unmodifiableSet(
        new HashSet<String>() {{
            add(".opus");
            add(".aac");
        }}
    );

    public static final Set<String> IMAGES = Collections.unmodifiableSet(
        new HashSet<String>() {{
            add(".jpg");
            add(".jpeg");
            add(".png");
            add(".gif");
            add(".webp");
            add(".bmp");
            add(".heic");
            add(".heif");
        }}
    );

    public static final Set<String> DOCUMENTS = Collections.unmodifiableSet(
        new HashSet<String>() {{
            add(".pdf");
            add(".docx");
            add(".doc");
            add(".xls");
            add(".xlsx");
            add(".ppt");
            add(".pptx");
            add(".txt");
            add(".csv");
            add(".rtf");
            add(".zip");
            add(".rar");
        }}
    );

    public static final Set<String> AUDIO = Collections.unmodifiableSet(
        new HashSet<String>() {{
            add(".mp3");
            add(".m4a");
            add(".wav");
            add(".ogg");
            add(".aac");
            add(".flac");
        }}
    );

    public static final Set<String> GALLERY = Collections.unmodifiableSet(
        new HashSet<String>() {{
            add(".jpg");
            add(".jpeg");
            add(".png");
            add(".gif");
            add(".webp");
            add(".bmp");
            add(".heic");
            add(".heif");
        }}
    );

    public static Set<String> forType(MediaType type) {
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
                return Collections.emptySet();
        }
    }
}
