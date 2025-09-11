package core.audio;

import com.google.errorprone.annotations.ThreadSafe;

/** Immutable handle to audio managed by the engine. */
@ThreadSafe
public interface AudioHandle {

    String getFilePath();

    /** False if evicted from cache. */
    boolean isValid();

    long getId();
}
