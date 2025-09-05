package events;

import lombok.Getter;
import lombok.NonNull;
import ui.audiofiles.AudioFile;

/** Request to load the specified AudioFile. */
@Getter
public class AudioFileLoadRequestedEvent {
    private final AudioFile file;

    public AudioFileLoadRequestedEvent(@NonNull AudioFile file) {
        this.file = file;
    }
}
