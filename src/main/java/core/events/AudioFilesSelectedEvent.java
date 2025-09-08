package core.events;

import java.io.File;

/**
 * Event published when audio files are selected for opening. The UI layer handles this by adding
 * the files to the audio file display.
 */
public record AudioFilesSelectedEvent(File[] files) {

    public AudioFilesSelectedEvent {
        if (files == null) {
            throw new IllegalArgumentException("Files cannot be null");
        }
    }

    /** Convenience constructor for a single file. */
    public AudioFilesSelectedEvent(File file) {
        this(new File[] {file});
    }
}
