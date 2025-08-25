package events;

import components.audiofiles.AudioFile;

/**
 * Event fired when an audio file is switched.
 *
 * <p>This event is published to EventDispatchBus when the current audio file changes.
 */
public record AudioFileSwitchedEvent(AudioFile file) {

    /**
     * Creates a new AudioFileSwitchedEvent.
     *
     * @param file The audio file that was switched to, or null if no file is selected
     */
    public AudioFileSwitchedEvent(AudioFile file) {
        this.file = file;
    }

    /**
     * Gets the audio file that was switched to.
     *
     * @return The audio file, or null if no file is selected
     */
    public AudioFile getFile() {
        return file;
    }
}
