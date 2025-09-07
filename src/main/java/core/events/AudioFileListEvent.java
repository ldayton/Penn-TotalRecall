package core.events;

import lombok.NonNull;

/**
 * Event for AudioFileList operations. This eliminates direct AudioFileList control from popup menus
 * and other components.
 */
public record AudioFileListEvent(@NonNull Type type, int index) {
    public enum Type {
        REMOVE_FILE_AT_INDEX
    }
}
