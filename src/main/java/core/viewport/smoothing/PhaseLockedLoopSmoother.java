package core.viewport.smoothing;

import core.audio.session.AudioSessionStateMachine;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase-locked loop (PLL) smoother for waveform playback. Locks onto the playback frequency and
 * maintains phase alignment with minimal lag. Particularly effective for continuous playback with
 * occasional timing variations.
 */
@Slf4j
public class PhaseLockedLoopSmoother implements PlayheadSmoother {

    /** Internal PLL state */
    private static class PLLState {
        final double phase; // Current phase (position)
        final double frequency; // Locked frequency (frames per ms)
        final double phaseError; // Accumulated phase error
        final long lastUpdateTime; // Last update timestamp
        final long lastTarget; // Last target from audio engine

        PLLState(
                double phase,
                double frequency,
                double phaseError,
                long lastUpdateTime,
                long lastTarget) {
            this.phase = phase;
            this.frequency = frequency;
            this.phaseError = phaseError;
            this.lastUpdateTime = lastUpdateTime;
            this.lastTarget = lastTarget;
        }
    }

    private final AtomicReference<PLLState> state =
            new AtomicReference<>(new PLLState(0, 44.1, 0, System.currentTimeMillis(), 0));

    // PLL tuning parameters
    private static final double PHASE_GAIN = 0.1; // How aggressively to correct phase errors
    private static final double FREQUENCY_GAIN = 0.001; // How quickly to adapt frequency
    private static final double MAX_PHASE_ERROR = 1000; // Maximum phase error before reset

    @Override
    public SmoothingResult updateAndGetSmoothedPosition(
            long targetFrame, long deltaMs, AudioSessionStateMachine.State audioState) {

        long currentTime = System.currentTimeMillis();
        boolean isPlaying = audioState == AudioSessionStateMachine.State.PLAYING;

        // Not playing - reset PLL
        if (!isPlaying) {
            state.set(new PLLState(targetFrame, 44.1, 0, currentTime, targetFrame));
            return new SmoothingResult(targetFrame, 0);
        }

        PLLState newState =
                state.updateAndGet(
                        current -> {
                            // Calculate time delta
                            long timeDelta = currentTime - current.lastUpdateTime;
                            if (timeDelta <= 0) timeDelta = 16; // ~60fps fallback

                            // Advance phase based on current frequency
                            double expectedPhase = current.phase + (current.frequency * timeDelta);

                            // Calculate phase error (how far we are from target)
                            double phaseError = targetFrame - expectedPhase;

                            // Check for lock loss (large jump)
                            if (Math.abs(phaseError) > MAX_PHASE_ERROR
                                    || Math.abs(targetFrame - current.lastTarget)
                                            > MAX_PHASE_ERROR) {
                                log.debug("PLL lock lost, resyncing to target: {}", targetFrame);
                                return new PLLState(
                                        targetFrame,
                                        current.frequency,
                                        0,
                                        currentTime,
                                        targetFrame);
                            }

                            // Update frequency based on phase error (integral term)
                            double newFrequency = current.frequency + (phaseError * FREQUENCY_GAIN);
                            // Clamp frequency to reasonable bounds (20kHz to 96kHz sample rates)
                            newFrequency = Math.max(20.0, Math.min(96.0, newFrequency));

                            // Apply phase correction (proportional term)
                            double correctedPhase = expectedPhase + (phaseError * PHASE_GAIN);

                            // Accumulate phase error for monitoring
                            double accumulatedError = current.phaseError * 0.95 + phaseError * 0.05;

                            log.trace(
                                    "PLL: phase error={:.1f}, frequency={:.3f} f/ms",
                                    phaseError,
                                    newFrequency);

                            return new PLLState(
                                    correctedPhase,
                                    newFrequency,
                                    accumulatedError,
                                    currentTime,
                                    targetFrame);
                        });

        long smoothedPosition = Math.round(newState.phase);
        long distance = targetFrame - smoothedPosition;

        log.trace(
                "PLL smoother: target={}, smoothed={}, distance={}, freq={:.2f} f/ms, error={:.1f}",
                targetFrame,
                smoothedPosition,
                distance,
                newState.frequency,
                newState.phaseError);

        return new SmoothingResult(smoothedPosition, distance);
    }

    @Override
    public void reset() {
        state.set(new PLLState(0, 44.1, 0, System.currentTimeMillis(), 0));
        log.debug("PLL smoother reset");
    }
}
