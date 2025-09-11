package state;

import core.state.InterpolatedPlaybackTracker;
import core.state.WaveformSessionDataSource;
import core.state.WaveformViewport;
import core.waveform.TimeRange;
import core.waveform.Waveform;
import core.waveform.WaveformPaintingDataSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;

/**
 * Aggregates data needed for waveform painting. Coordinates between WaveformManager,
 * WaveformViewport, and WaveformSessionSource.
 */
@Singleton
public class WaveformPaintDataSource implements WaveformPaintingDataSource {

    private final WaveformManager waveformManager;
    private final WaveformViewport viewport;
    private final WaveformSessionDataSource sessionSource;
    private final InterpolatedPlaybackTracker playbackTracker;

    @Inject
    public WaveformPaintDataSource(
            @NonNull WaveformManager waveformManager,
            @NonNull WaveformViewport viewport,
            @NonNull WaveformSessionDataSource sessionSource,
            @NonNull InterpolatedPlaybackTracker playbackTracker) {
        this.waveformManager = waveformManager;
        this.viewport = viewport;
        this.sessionSource = sessionSource;
        this.playbackTracker = playbackTracker;
    }

    /**
     * Prepare for the next paint frame. Updates viewport position based on current playback state.
     * Should be called before each paint operation.
     */
    public void prepareFrame() {
        // Get real playback position and update interpolator
        double realPosition = sessionSource.getPlaybackPosition().orElse(0.0);
        double totalDuration = sessionSource.getTotalDuration().orElse(0.0);
        boolean isPlaying = sessionSource.isPlaying();

        // Update playing state
        playbackTracker.setPlaying(isPlaying);

        // Always update real position to stay in sync with AudioSessionManager
        playbackTracker.updateRealPosition(realPosition);

        // Use interpolated position for smooth viewport scrolling
        double smoothPosition = playbackTracker.getInterpolatedPosition();

        viewport.followPlayback(smoothPosition, totalDuration, isPlaying);
    }

    /**
     * Handle seek events to reset interpolation. This should be called when the user seeks to a new
     * position.
     */
    public void onSeek(double positionSeconds) {
        playbackTracker.reset(positionSeconds);
    }

    /** Update viewport width from canvas. Should be called when canvas size changes. */
    public void updateViewportWidth(int widthPixels) {
        viewport.setWidth(widthPixels);
    }

    // WaveformPaintingDataSource implementation - all pure queries

    @Override
    public TimeRange getTimeRange() {
        // Return null if no audio loaded (no valid time range)
        if (!sessionSource.isAudioLoaded()) {
            return null;
        }
        return viewport.getTimeRange();
    }

    @Override
    public int getPixelsPerSecond() {
        return viewport.getPixelsPerSecond();
    }

    @Override
    public double getPlaybackPositionSeconds() {
        return sessionSource.getPlaybackPosition().orElse(0.0);
    }

    @Override
    public Waveform getWaveform() {
        return waveformManager.getCurrentWaveform().orElse(null);
    }

    @Override
    public boolean isPlaying() {
        return sessionSource.isPlaying();
    }
}
