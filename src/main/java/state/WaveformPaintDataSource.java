package state;

import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.PlaybackHandle;
import core.audio.PlaybackListener;
import core.audio.PlaybackState;
import core.state.WaveformSessionDataSource;
import core.state.ViewportPositionManager;
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
    private final ViewportPositionManager viewport;
    private final WaveformSessionDataSource sessionSource;
    private final Provider<AudioEngine> audioEngineProvider;

    // Cached position from progress callbacks (frames)
    private volatile long progressCallbackPositionFrames = 0L;
    private volatile long progressCallbackTotalFrames = 0L;
    // Sample rate for frame-to-seconds conversion
    private volatile int cachedSampleRate = 44100;

    @Inject
    public WaveformPaintDataSource(
            @NonNull WaveformManager waveformManager,
            @NonNull ViewportPositionManager viewport,
            @NonNull WaveformSessionDataSource sessionSource,
            @NonNull Provider<AudioEngine> audioEngineProvider) {
        this.waveformManager = waveformManager;
        this.viewport = viewport;
        this.sessionSource = sessionSource;
        this.audioEngineProvider = audioEngineProvider;

        // Register as PlaybackListener immediately to avoid missing initial progress events
        AudioEngine engine = audioEngineProvider.get();
        engine.addPlaybackListener(this);
    }

    /**
     * Prepare for the next paint frame. Updates viewport position based on current playback state.
     * Should be called before each paint operation.
     */
    public void prepareFrame() {

        // Update sample rate if available
        sessionSource.getSampleRate().ifPresent(rate -> cachedSampleRate = rate);

        // Determine playback position source based on play state
        boolean isPlaying = sessionSource.isPlaying();
        double realPosition;
        if (isPlaying) {
            // When playing, prefer cached position from progress callbacks for smooth rendering
            double cachedPosition = getProgressCallbackPositionSeconds();
            // But if cached position is 0 and we're playing, use actual position to avoid lag
            // This handles the case when playback just started and callbacks haven't arrived yet
            if (cachedPosition == 0.0) {
                realPosition = sessionSource.getPlaybackPosition().orElse(0.0);
            } else {
                realPosition = cachedPosition;
            }
        } else {
            // When not playing (READY/PAUSED), use session source position
            realPosition = sessionSource.getPlaybackPosition().orElse(0.0);
        }
        double totalDuration = sessionSource.getTotalDuration().orElse(0.0);

        // Update viewport to follow playback with centered playhead
        viewport.followPlayback(realPosition, totalDuration, isPlaying);

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
        // Return cached position from progress callbacks, converted to seconds
        return getProgressCallbackPositionSeconds();
    }

    /**
     * Get the cached position from progress callbacks converted to seconds. This is updated every
     * ~15ms and used for smooth UI rendering.
     */
    private double getProgressCallbackPositionSeconds() {
        // Convert cached frames to seconds
        if (progressCallbackTotalFrames == 0L || cachedSampleRate == 0) {
            // No audio loaded or no progress updates yet
            return 0.0;
        }
        return (double) progressCallbackPositionFrames / cachedSampleRate;
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
    public void onStateChanged(
            @NonNull PlaybackHandle playback,
            @NonNull PlaybackState newState,
            @NonNull PlaybackState oldState) {
        // When playback stops or finishes, clear cached progress so UI doesn't stick on old value
        if (newState == PlaybackState.STOPPED || newState == PlaybackState.FINISHED) {
            this.progressCallbackPositionFrames = 0L;
            this.progressCallbackTotalFrames = 0L;
        }
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
