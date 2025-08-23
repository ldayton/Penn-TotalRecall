package control;

import components.audiofiles.AudioFile;

/** Base event class for audio state changes. */
public abstract class AudioStateEvent {
    private final AudioFile file;
    private final long timestamp;

    protected AudioStateEvent(AudioFile file) {
        this.file = file;
        this.timestamp = System.currentTimeMillis();
    }

    public AudioFile getFile() {
        return file;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
