package core.events;

import java.io.File;
import lombok.NonNull;

/** Event published when a wordpool file is selected to be loaded. */
public record WordpoolFileSelectedEvent(@NonNull File file) {}
