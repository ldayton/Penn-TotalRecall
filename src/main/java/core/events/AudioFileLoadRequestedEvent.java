package core.events;

import java.io.File;
import lombok.Getter;
import lombok.NonNull;

/** Request to load the specified audio file. */
@Getter
public class AudioFileLoadRequestedEvent {
    private final File file;

    public AudioFileLoadRequestedEvent(@NonNull File file) {
        this.file = file;
    }
}
