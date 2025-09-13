package core.viewport;

import core.audio.PlaybackHandle;
import core.audio.PlaybackListener;
import core.audio.PlaybackState;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens to playback events and updates the viewport accordingly. Created per audio session to
 * track playback for viewport synchronization.
 */
@Slf4j
public class ViewportPlaybackListener implements PlaybackListener {

    private final ViewportSessionManager viewportManager;
    private final int sampleRate;

    // Track playback state
    private volatile long currentPositionFrames = 0L;
    private volatile boolean isPlaying = false;

    public ViewportPlaybackListener(
            @NonNull ViewportSessionManager viewportManager, int sampleRate) {
        this.viewportManager = viewportManager;
        this.sampleRate = sampleRate;
    }

    @Override
    public void onProgress(
            @NonNull PlaybackHandle playback, long positionFrames, long totalFrames) {
        this.currentPositionFrames = positionFrames;

        // Update viewport position when playing
        if (isPlaying) {
            double positionSeconds = positionFrames / (double) sampleRate;
            viewportManager.onPlaybackUpdate(positionSeconds);
        }
    }

    @Override
    public void onStateChanged(
            @NonNull PlaybackHandle playback,
            @NonNull PlaybackState newState,
            @NonNull PlaybackState oldState) {
        this.isPlaying = (newState == PlaybackState.PLAYING);

        // Clear position when stopped and update viewport to position 0
        if (newState == PlaybackState.STOPPED || newState == PlaybackState.FINISHED) {
            this.currentPositionFrames = 0L;
            viewportManager.onPlaybackUpdate(0.0);
        }

        log.debug("Playback state changed: {} -> {}, isPlaying: {}", oldState, newState, isPlaying);
    }

    /** Get current playback position in seconds. */
    public double getPlaybackPositionSeconds() {
        return currentPositionFrames / (double) sampleRate;
    }

    /** Check if currently playing. */
    public boolean isPlaying() {
        return isPlaying;
    }
}
