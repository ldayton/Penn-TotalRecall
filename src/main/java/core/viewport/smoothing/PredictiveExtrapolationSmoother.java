package core.viewport.smoothing;

import core.audio.session.AudioSessionStateMachine;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Predictive playhead smoother for smooth waveform scrolling during playback. Uses extrapolation
 * during playback to eliminate lag, and instant snapping for seeks. Thread-safe implementation
 * using atomic operations.
 */
@Slf4j
public class PredictiveExtrapolationSmoother implements PlayheadSmoother {

    /** Internal state for atomic updates */
    private static class State {
        final long position; // Current smoothed position
        final long lastTargetFrame; // Last known target position
        final long lastUpdateTimeMs; // Time of last update
        final boolean wasPlaying; // Whether we were playing in the last update

        State(long position, long lastTargetFrame, long lastUpdateTimeMs, boolean wasPlaying) {
            this.position = position;
            this.lastTargetFrame = lastTargetFrame;
            this.lastUpdateTimeMs = lastUpdateTimeMs;
            this.wasPlaying = wasPlaying;
        }
    }

    private final AtomicReference<State> state =
            new AtomicReference<>(new State(0, 0, System.currentTimeMillis(), false));

    // Sample rate - should be injected or determined from audio context
    // Using 44100 as default for now (44.1kHz)
    private static final double SAMPLE_RATE = 44100.0; // frames per second during playback
    private static final long RESYNC_THRESHOLD =
            1000; // Resync if off by more than this many frames

    @Override
    public SmoothingResult updateAndGetSmoothedPosition(
            long targetFrame, long deltaMs, AudioSessionStateMachine.State audioState) {

        long currentTimeMs = System.currentTimeMillis();
        boolean isPlaying = audioState == AudioSessionStateMachine.State.PLAYING;

        // Handle non-playing states - just snap to target
        if (!isPlaying) {
            State newState = new State(targetFrame, targetFrame, currentTimeMs, false);
            state.set(newState);
            return new SmoothingResult(targetFrame, 0);
        }

        // Playing state - use predictive extrapolation
        State newState =
                state.updateAndGet(
                        current -> {
                            // If we just started playing or had a large jump (seek), sync to target
                            if (!current.wasPlaying
                                    || Math.abs(targetFrame - current.lastTargetFrame)
                                            > RESYNC_THRESHOLD) {
                                log.debug(
                                        "Resyncing to target: {} (was at {})",
                                        targetFrame,
                                        current.position);
                                return new State(targetFrame, targetFrame, currentTimeMs, true);
                            }

                            // Calculate expected advancement based on elapsed time
                            long elapsedMs = currentTimeMs - current.lastUpdateTimeMs;
                            double expectedAdvancement = (elapsedMs / 1000.0) * SAMPLE_RATE;

                            // Extrapolate position based on playback rate
                            long extrapolatedPosition =
                                    current.position + Math.round(expectedAdvancement);

                            // Check drift from actual position
                            long drift = targetFrame - extrapolatedPosition;

                            // If drift is too large, resync
                            if (Math.abs(drift) > RESYNC_THRESHOLD) {
                                log.debug("Large drift detected: {} frames, resyncing", drift);
                                return new State(targetFrame, targetFrame, currentTimeMs, true);
                            }

                            // Apply gentle correction for small drift (10% correction per frame)
                            long correctedPosition = extrapolatedPosition + Math.round(drift * 0.1);

                            return new State(correctedPosition, targetFrame, currentTimeMs, true);
                        });

        long distance = targetFrame - newState.position;

        log.trace(
                "Predictive smoother: target={}, smoothed={}, distance={} frames, playing={}",
                targetFrame,
                newState.position,
                distance,
                isPlaying);

        return new SmoothingResult(newState.position, distance);
    }

    @Override
    public void reset() {
        state.set(new State(0, 0, System.currentTimeMillis(), false));
        log.debug("Predictive smoother reset");
    }
}
