package come.tshah.app;

public enum MediaType {
    VOICE("voice"),
    IMAGES("images"),
    DOCUMENTS("documents"),
    AUDIO("audio"),
    GALLERY("gallery");

    private final String typeName;

    MediaType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

    public static MediaType fromString(String value) {
        if (value == null) return null;
        for (MediaType t : values()) {
            if (t.typeName.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
