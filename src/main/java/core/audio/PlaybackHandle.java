package audio;

import app.annotations.ThreadSafe;

/** Handle to active playback. Keeps audio in cache while playing. */
@ThreadSafe
public interface PlaybackHandle extends AutoCloseable {

    AudioHandle getAudioHandle();

    long getStartFrame();

    long getEndFrame();

    long getId();

    boolean isActive();

    @Override
    void close();
}
