package a2;

import app.annotations.ThreadSafe;

/** Immutable handle to audio managed by the engine. */
@ThreadSafe
public interface AudioHandle {

    String getFilePath();

    /** False if evicted from cache. */
    boolean isValid();

    long getId();
}
