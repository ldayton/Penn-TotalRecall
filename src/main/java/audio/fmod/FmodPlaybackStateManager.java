package a2.fmod;

import a2.PlaybackState;
import a2.exceptions.AudioPlaybackException;
import app.annotations.ThreadSafe;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages playback state transitions and enforces valid state machine transitions for audio
 * playback. This class ensures that all playback state changes follow the defined state machine
 * rules and are properly synchronized.
 *
 * <p>State machine transitions:
 *
 * <pre>
 * STOPPED -> PLAYING (via play)
 * PLAYING -> PAUSED (via pause)
 * PLAYING -> STOPPED (via stop or end of track)
 * PAUSED -> PLAYING (via resume)
 * PAUSED -> STOPPED (via stop)
 * </pre>
 */
@ThreadSafe
@Slf4j
class FmodPlaybackStateManager {

    private final ReentrantLock stateLock = new ReentrantLock();
    private volatile PlaybackState currentState = PlaybackState.STOPPED;

    /**
     * Get the current playback state.
     *
     * @return The current playback state
     */
    PlaybackState getCurrentState() {
        return currentState;
    }

    /**
     * Check if playback is currently active (playing, paused, or seeking).
     *
     * @return true if playback is active, false if stopped
     */
    boolean isActive() {
        PlaybackState state = currentState;
        return state != PlaybackState.STOPPED && state != PlaybackState.FINISHED;
    }

    /**
     * Transition to PLAYING state from STOPPED.
     *
     * @throws AudioPlaybackException if the transition is invalid
     */
    void transitionToPlaying() {
        stateLock.lock();
        try {
            if (currentState != PlaybackState.STOPPED && currentState != PlaybackState.FINISHED) {
                throw new AudioPlaybackException(
                        "Cannot start playback from state: " + currentState);
            }
            currentState = PlaybackState.PLAYING;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Transition to PAUSED state from PLAYING.
     *
     * @throws AudioPlaybackException if the transition is invalid
     */
    void transitionToPaused() {
        stateLock.lock();
        try {
            if (currentState != PlaybackState.PLAYING) {
                throw new AudioPlaybackException("Cannot pause from state: " + currentState);
            }
            currentState = PlaybackState.PAUSED;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Resume playback - transition from PAUSED back to PLAYING.
     *
     * @throws AudioPlaybackException if the transition is invalid
     */
    void resume() {
        stateLock.lock();
        try {
            if (currentState != PlaybackState.PAUSED) {
                throw new AudioPlaybackException("Cannot resume from state: " + currentState);
            }
            currentState = PlaybackState.PLAYING;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Transition to STOPPED state from any state. This is the "nuclear option" - stop everything
     * regardless of current state.
     */
    void transitionToStopped() {
        stateLock.lock();
        try {
            if (currentState == PlaybackState.STOPPED) {
                return;
            }
            currentState = PlaybackState.STOPPED;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Validate that seeking is allowed in the current state. Seeking is allowed from PLAYING or
     * PAUSED states. Does not change state since FMOD seeks are instant.
     *
     * @throws AudioPlaybackException if seeking is not allowed from current state
     */
    void validateSeekAllowed() {
        stateLock.lock();
        try {
            if (currentState != PlaybackState.PLAYING && currentState != PlaybackState.PAUSED) {
                throw new AudioPlaybackException("Cannot seek from state: " + currentState);
            }
            // No state change - seek is instant in FMOD
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Transition to FINISHED state when playback completes naturally.
     *
     * @throws AudioPlaybackException if the transition is invalid
     */
    void transitionToFinished() {
        stateLock.lock();
        try {
            if (currentState != PlaybackState.PLAYING) {
                // Can only finish from playing state
                throw new AudioPlaybackException(
                        "Cannot finish playback from state: " + currentState);
            }
            currentState = PlaybackState.FINISHED;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Force transition to STOPPED when FMOD channel becomes invalid. Used when FMOD operations
     * return FMOD_ERR_INVALID_HANDLE. This ensures state stays synchronized with actual FMOD state.
     */
    void handleChannelInvalid() {
        stateLock.lock();
        try {
            if (currentState != PlaybackState.STOPPED && currentState != PlaybackState.FINISHED) {
                currentState = PlaybackState.STOPPED;
            }
        } finally {
            stateLock.unlock();
        }
    }

    /** Reset the state machine to STOPPED. Used for cleanup or error recovery. */
    void reset() {
        stateLock.lock();
        try {
            PlaybackState oldState = currentState;
            currentState = PlaybackState.STOPPED;
            if (oldState != PlaybackState.STOPPED) {}
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Check if the state machine is in the expected state.
     *
     * @param expected The expected state
     * @throws AudioPlaybackException if the current state doesn't match
     */
    void checkState(PlaybackState expected) {
        if (currentState != expected) {
            throw new AudioPlaybackException(
                    String.format(
                            "Operation requires state %s but current state is %s",
                            expected, currentState));
        }
    }

    /**
     * Atomically check if in the expected state and transition to a new state.
     *
     * @param expected The expected current state
     * @param newState The new state to transition to
     * @return true if the transition succeeded, false if current state didn't match expected
     */
    boolean compareAndSetState(PlaybackState expected, PlaybackState newState) {
        stateLock.lock();
        try {
            if (currentState == expected) {
                // Validate the transition is allowed
                if (!isValidTransition(expected, newState)) {
                    log.warn("Invalid transition attempt: {} -> {}", expected, newState);
                    return false;
                }
                currentState = newState;
                return true;
            }
            return false;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Validate if a state transition is allowed according to the state machine rules.
     *
     * @param from The source state
     * @param to The target state
     * @return true if the transition is valid
     */
    private boolean isValidTransition(PlaybackState from, PlaybackState to) {
        return switch (from) {
            case STOPPED -> to == PlaybackState.PLAYING;
            case FINISHED -> to == PlaybackState.PLAYING || to == PlaybackState.STOPPED;
            case PLAYING ->
                    to == PlaybackState.PAUSED
                            || to == PlaybackState.STOPPED
                            || to == PlaybackState.FINISHED;
            case PAUSED -> to == PlaybackState.PLAYING || to == PlaybackState.STOPPED;
            case SEEKING -> false; // SEEKING state no longer used
            default -> false; // Handle any future states
        };
    }
}
