package control;

import components.audiofiles.AudioFile;

/** Event fired when an audio file is switched. */
public class AudioFileSwitchedEvent extends AudioStateEvent {

    public AudioFileSwitchedEvent(AudioFile file) {
        super(file);
    }
}
