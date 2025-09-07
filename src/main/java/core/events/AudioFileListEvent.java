package core.events;

/**
 * Event for AudioFileList operations. This eliminates direct AudioFileList control from popup menus
 * and other components.
 */
public class AudioFileListEvent {
    public enum Type {
        REMOVE_FILE_AT_INDEX
    }

    private final Type type;
    private final int index;

    public AudioFileListEvent(Type type, int index) {
        this.type = type;
        this.index = index;
    }

    public Type getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }
}
