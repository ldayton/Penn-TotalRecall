package core.audio.fmod;

import core.audio.AudioHandle;
import core.audio.PlaybackHandle;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.NonNull;

/** FMOD implementation of PlaybackHandle for managing active playback. */
class FmodPlaybackHandle implements PlaybackHandle {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    @Getter private final long id;
    @Getter @NonNull private final AudioHandle audioHandle;
    @Getter private final long startFrame;
    @Getter private final long endFrame;

    private final MemorySegment channel;
    private final AtomicBoolean active;

    FmodPlaybackHandle(
            @NonNull AudioHandle audioHandle,
            @NonNull MemorySegment channel,
            long startFrame,
            long endFrame) {
        this.id = ID_GENERATOR.incrementAndGet();
        this.audioHandle = audioHandle;
        this.channel = channel;
        this.startFrame = startFrame;
        this.endFrame = endFrame;
        this.active = new AtomicBoolean(true);
    }

    MemorySegment getChannel() {
        return channel;
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    void markInactive() {
        active.set(false);
    }

    @Override
    public void close() {
        markInactive();
    }
}
