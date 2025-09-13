package state;

import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.PlaybackHandle;
import core.audio.PlaybackListener;
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
public class WaveformPaintDataSource implements WaveformPaintingDataSource, PlaybackListener {

    private final WaveformManager waveformManager;
    private final WaveformViewport viewport;
    private final WaveformSessionDataSource sessionSource;
    private final Provider<AudioEngine> audioEngineProvider;

    // Cached position from progress callbacks
    private volatile long progressCallbackPositionFrames = 0;
    private volatile long progressCallbackTotalFrames = 0;
    private boolean listenerRegistered = false;

    @Inject
    public WaveformPaintDataSource(
            @NonNull WaveformManager waveformManager,
            @NonNull WaveformViewport viewport,
            @NonNull WaveformSessionDataSource sessionSource,
            @NonNull Provider<AudioEngine> audioEngineProvider) {
        this.waveformManager = waveformManager;
        this.viewport = viewport;
        this.sessionSource = sessionSource;
        this.audioEngineProvider = audioEngineProvider;
    }

    /**
     * Prepare for the next paint frame. Updates viewport position based on current playback state.
     * Should be called before each paint operation.
     */
    public void prepareFrame() {
        // Ensure we're registered as a listener
        ensureListenerRegistered();

        // Use cached position from progress callbacks for smooth 60 FPS rendering
        double realPosition = getProgressCallbackPosition();
        double totalDuration = sessionSource.getTotalDuration().orElse(0.0);
        boolean isPlaying = sessionSource.isPlaying();

        // Update viewport to follow playback with centered playhead
        viewport.followPlayback(realPosition, totalDuration, isPlaying);
    }

    /**
     * Ensure this instance is registered as a playback listener. Called lazily on first use to
     * avoid circular dependency issues.
     */
    private void ensureListenerRegistered() {
        if (!listenerRegistered) {
            AudioEngine engine = audioEngineProvider.get();
            engine.addPlaybackListener(this);
            listenerRegistered = true;
        }
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
        // Return cached position from progress callbacks
        return getProgressCallbackPosition();
    }

    /**
     * Get the cached position from progress callbacks. This is updated every ~15ms and used for
     * smooth UI rendering.
     */
    private double getProgressCallbackPosition() {
        // Convert cached frames to seconds
        var sampleRateOpt = sessionSource.getSampleRate();
        if (sampleRateOpt.isEmpty() || progressCallbackTotalFrames == 0) {
            // No audio loaded or no progress updates yet
            return 0.0;
        }

        int sampleRate = sampleRateOpt.get();
        return (double) progressCallbackPositionFrames / sampleRate;
    }

    // PlaybackListener implementation

    @Override
    public void onProgress(
            @NonNull PlaybackHandle playback, long positionFrames, long totalFrames) {
        // Cache the position for smooth UI rendering
        this.progressCallbackPositionFrames = positionFrames;
        this.progressCallbackTotalFrames = totalFrames;
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
