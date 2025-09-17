package core.viewport.smoothing;

import core.audio.session.AudioSessionStateMachine;

/**
 * Strategy interface for smoothing playhead position during viewport updates. Implementations
 * provide different smoothing algorithms to create smooth waveform scrolling during playback.
 * Thread-safe implementations required as updates come from audio thread and reads from UI thread.
 */
public interface PlayheadSmoother {

    /**
     * Result of smoothing calculation, returned atomically.
     *
     * @param smoothedFrame The smoothed frame position to use for rendering
     * @param distanceFromTarget Distance from true playhead (positive = behind, negative = ahead)
     */
    record SmoothingResult(long smoothedFrame, long distanceFromTarget) {}

    /**
     * Update smoother with current state and get smoothed position.
     *
     * @param targetFrame The actual/true playhead position from audio engine
     * @param deltaMs Time since last update in milliseconds
     * @param state Current audio session state
     * @return The smoothed position and distance from target
     */
    SmoothingResult updateAndGetSmoothedPosition(
            long targetFrame, long deltaMs, AudioSessionStateMachine.State state);

    /** Reset smoother to initial state. Called when audio stops or changes. */
    void reset();
}
