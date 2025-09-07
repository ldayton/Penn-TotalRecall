package core.events;

import java.io.File;
import lombok.Getter;
import lombok.NonNull;

/** Event published when a wordpool file is selected to be loaded. */
@Getter
public class WordpoolFileSelectedEvent {
    private final File file;

    public WordpoolFileSelectedEvent(@NonNull File file) {
        this.file = file;
    }
}
