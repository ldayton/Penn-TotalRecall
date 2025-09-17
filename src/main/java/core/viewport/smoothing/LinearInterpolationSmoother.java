package core.viewport.smoothing;

import core.audio.session.AudioSessionStateMachine;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Linear interpolation smoother for smooth waveform scrolling during playback. Interpolates between
 * playhead updates to create smooth motion at display framerate. Optimized specifically for
 * continuous playback scenarios.
 */
@Slf4j
public class LinearInterpolationSmoother implements PlayheadSmoother {

    /** Internal state for atomic updates */
    private static class State {
        final long previousTarget; // Previous target frame from audio engine
        final long currentTarget; // Current target frame from audio engine
        final long targetUpdateTime; // When we received the current target
        final double playbackRate; // Calculated playback rate (frames per ms)

        State(long previousTarget, long currentTarget, long targetUpdateTime, double playbackRate) {
            this.previousTarget = previousTarget;
            this.currentTarget = currentTarget;
            this.targetUpdateTime = targetUpdateTime;
            this.playbackRate = playbackRate;
        }
    }

    private final AtomicReference<State> state =
            new AtomicReference<>(
                    new State(0, 0, System.currentTimeMillis(), 44.1)); // 44.1 frames/ms = 44100 Hz

    @Override
    public SmoothingResult updateAndGetSmoothedPosition(
            long targetFrame, long deltaMs, AudioSessionStateMachine.State audioState) {

        long currentTime = System.currentTimeMillis();
        boolean isPlaying = audioState == AudioSessionStateMachine.State.PLAYING;

        // Not playing - snap to target
        if (!isPlaying) {
            state.set(new State(targetFrame, targetFrame, currentTime, 44.1));
            return new SmoothingResult(targetFrame, 0);
        }

        // Update state and calculate interpolated position
        State newState =
                state.updateAndGet(
                        current -> {
                            // Check if target has actually changed (new update from audio engine)
                            if (targetFrame != current.currentTarget) {
                                // Calculate actual playback rate from the last two updates
                                long framesDelta = targetFrame - current.currentTarget;
                                long timeDelta = currentTime - current.targetUpdateTime;

                                double newRate;
                                if (timeDelta > 0 && framesDelta > 0) {
                                    // Calculate observed playback rate
                                    newRate = (double) framesDelta / timeDelta;
                                    // Smooth the rate change to avoid jitter (blend with previous
                                    // rate) - more aggressive update for lower lag
                                    newRate = current.playbackRate * 0.3 + newRate * 0.7;
                                } else {
                                    // Keep existing rate
                                    newRate = current.playbackRate;
                                }

                                log.trace(
                                        "Audio update: {} -> {}, calculated rate: {:.1f} frames/ms",
                                        current.currentTarget,
                                        targetFrame,
                                        newRate);

                                return new State(
                                        current.currentTarget, targetFrame, currentTime, newRate);
                            }
                            // No new update from audio engine, keep current state
                            return current;
                        });

        // Calculate expected position based on time elapsed
        long timeSinceUpdate = currentTime - newState.targetUpdateTime;
        long expectedAdvance = Math.round(timeSinceUpdate * newState.playbackRate);

        // Extrapolate from the last known target position
        long extrapolatedPosition = newState.currentTarget + expectedAdvance;

        // Blend between extrapolated position and actual target to reduce lag
        // Use weighted average: favor actual target more to reduce lag
        double blendFactor = 0.8; // 80% weight to actual target, 20% to extrapolation
        long smoothedPosition =
                Math.round(targetFrame * blendFactor + extrapolatedPosition * (1.0 - blendFactor));

        // Ensure we don't fall too far behind
        long maxLagFrames = Math.round(newState.playbackRate * 10); // Max 10ms lag
        if (targetFrame - smoothedPosition > maxLagFrames) {
            smoothedPosition = targetFrame - maxLagFrames;
        }

        long distance = targetFrame - smoothedPosition;

        log.trace(
                "Linear interpolation: target={}, smoothed={}, distance={}, rate={:.1f} f/ms",
                targetFrame,
                smoothedPosition,
                distance,
                newState.playbackRate);

        return new SmoothingResult(smoothedPosition, distance);
    }

    @Override
    public void reset() {
        state.set(new State(0, 0, System.currentTimeMillis(), 44.1));
        log.debug("Linear interpolation smoother reset");
    }
}
