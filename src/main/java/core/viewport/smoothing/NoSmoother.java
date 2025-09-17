package core.viewport.smoothing;

import core.audio.session.AudioSessionStateMachine;
import lombok.extern.slf4j.Slf4j;

/**
 * Pass-through smoother that performs no smoothing. Useful for baseline comparison and debugging to
 * see raw playhead behavior.
 */
@Slf4j
public class NoSmoother implements PlayheadSmoother {

    @Override
    public SmoothingResult updateAndGetSmoothedPosition(
            long targetFrame, long deltaMs, AudioSessionStateMachine.State audioState) {

        // Always return the exact target position with zero distance
        log.trace("No smoothing: target={}, distance=0", targetFrame);
        return new SmoothingResult(targetFrame, 0);
    }

    @Override
    public void reset() {
        log.debug("No smoother reset (no-op)");
    }
}
