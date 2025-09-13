package state;

import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.PlaybackHandle;
import core.audio.PlaybackListener;
import core.audio.PlaybackState;
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

    // Cached hearing-time position from progress callbacks (seconds)
    private volatile double progressCallbackPositionSeconds = 0.0;
    private volatile double progressCallbackTotalSeconds = 0.0;
    private boolean listenerRegistered = false;
    // Track last known play state to detect transitions
    private boolean lastIsPlaying = false;

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

        // Determine playback position source based on play state
        boolean isPlaying = sessionSource.isPlaying();
        double realPosition =
                isPlaying
                        // When playing, use cached position from frequent progress callbacks for
                        // smooth rendering
                        ? getProgressCallbackPosition()
                        // When not playing (READY/PAUSED), fall back to session source position
                        : sessionSource.getPlaybackPosition().orElse(0.0);
        double totalDuration = sessionSource.getTotalDuration().orElse(0.0);

        // Debug: on transition to PLAYING, report large deltas between DSP(progress) and Engine
        if (isPlaying && !lastIsPlaying) {
            double dspSeconds = getProgressCallbackPosition();
            double engineSeconds = sessionSource.getPlaybackPosition().orElse(-1.0);
            if (engineSeconds >= 0) {
                double delta = Math.abs(dspSeconds - engineSeconds);
                // Consider jumps >100ms as notable
                if (delta > 0.10) {
                    System.out.println(
                            String.format(
                                    "[Waveform DEBUG] State PLAYING: DSP(progress)=%.6fs,"
                                            + " Engine=%.6fs, Δ=%.6fs",
                                    dspSeconds, engineSeconds, delta));
                }
            }
        }

        // Update viewport to follow playback with centered playhead
        viewport.followPlayback(realPosition, totalDuration, isPlaying);

        // Remember state for next frame
        lastIsPlaying = isPlaying;
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
        if (progressCallbackTotalSeconds == 0.0) {
            // No audio loaded or no progress updates yet
            return 0.0;
        }
        return progressCallbackPositionSeconds;
    }

    // PlaybackListener implementation

    @Override
    public void onProgress(
            @NonNull PlaybackHandle playback, double hearingSeconds, double totalSeconds) {
        // Cache the position for smooth UI rendering (hearing-time seconds)
        this.progressCallbackPositionSeconds = hearingSeconds;
        this.progressCallbackTotalSeconds = totalSeconds;
    }

    @Override
    public void onStateChanged(
            @NonNull PlaybackHandle playback,
            @NonNull PlaybackState newState,
            @NonNull PlaybackState oldState) {
        // When playback stops or finishes, clear cached progress so UI doesn't stick on old value
        if (newState == PlaybackState.STOPPED || newState == PlaybackState.FINISHED) {
            this.progressCallbackPositionSeconds = 0.0;
            this.progressCallbackTotalSeconds = 0.0;
        }

        // Debug: detect discrepancies between DSP progress (cached) and engine/session position
        if (newState == PlaybackState.PAUSED || newState == PlaybackState.PLAYING) {
            double dspSeconds = getProgressCallbackPosition();
            double engineSeconds = sessionSource.getPlaybackPosition().orElse(-1.0);
            if (engineSeconds >= 0) {
                double delta = Math.abs(dspSeconds - engineSeconds);
                // Print only if notable discrepancy (> 20ms)
                if (delta > 0.02) {
                    System.out.println(
                            String.format(
                                    "[Waveform DEBUG] State %s: DSP(progress)=%.6fs, Engine=%.6fs,"
                                            + " Δ=%.6fs",
                                    newState, dspSeconds, engineSeconds, delta));
                }
            }
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
