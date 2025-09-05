package s2;

import app.annotations.ThreadSafe;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * State machine for audio session transitions. This class ensures that all session state changes
 * follow the defined state machine rules and are properly synchronized.
 *
 * <p>State machine transitions:
 *
 * <pre>
 * NO_AUDIO -> LOADING (load file)
 * LOADING -> READY (success)
 * LOADING -> ERROR (failed)
 * LOADING -> NO_AUDIO (cancelled)
 *
 * READY -> PLAYING (play)
 * READY -> NO_AUDIO (close)
 * READY -> LOADING (switch file)
 *
 * PLAYING -> PAUSED (pause)
 * PLAYING -> READY (stop/finished)
 * PLAYING -> ERROR (playback error)
 *
 * PAUSED -> PLAYING (resume)
 * PAUSED -> READY (stop)
 *
 * ERROR -> NO_AUDIO (reset)
 * ERROR -> LOADING (retry/new file)
 * </pre>
 */
@ThreadSafe
@Slf4j
public class AudioSessionStateMachine {

    public enum State {
        NO_AUDIO, // No file loaded
        LOADING, // Loading audio file
        READY, // Audio loaded, stopped
        PLAYING, // Main playback active
        PAUSED, // Main playback paused
        ERROR // Error state
    }

    private final ReentrantLock stateLock = new ReentrantLock();
    private volatile State currentState = State.NO_AUDIO;

    /**
     * Get the current application state.
     *
     * @return The current application state
     */
    public State getCurrentState() {
        return currentState;
    }

    /**
     * Check if audio is loaded (not in NO_AUDIO, LOADING, or ERROR states).
     *
     * @return true if audio is loaded and available
     */
    public boolean isAudioLoaded() {
        State state = currentState;
        return state == State.READY || state == State.PLAYING || state == State.PAUSED;
    }

    /**
     * Check if playback is active (playing or paused).
     *
     * @return true if playback is active
     */
    public boolean isPlaybackActive() {
        State state = currentState;
        return state == State.PLAYING || state == State.PAUSED;
    }

    /**
     * Transition to LOADING state from NO_AUDIO or ERROR.
     *
     * @throws IllegalStateException if the transition is invalid
     */
    public void transitionToLoading() {
        stateLock.lock();
        try {
            if (currentState != State.NO_AUDIO
                    && currentState != State.ERROR
                    && currentState != State.READY) {
                throw new IllegalStateException("Cannot start loading from state: " + currentState);
            }
            log.debug("State transition: {} -> LOADING", currentState);
            currentState = State.LOADING;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Transition to READY state from LOADING or when stopping from PLAYING/PAUSED.
     *
     * @throws IllegalStateException if the transition is invalid
     */
    public void transitionToReady() {
        stateLock.lock();
        try {
            if (currentState != State.LOADING
                    && currentState != State.PLAYING
                    && currentState != State.PAUSED) {
                throw new IllegalStateException(
                        "Cannot transition to READY from state: " + currentState);
            }
            log.debug("State transition: {} -> READY", currentState);
            currentState = State.READY;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Transition to PLAYING state from READY or resuming from PAUSED.
     *
     * @throws IllegalStateException if the transition is invalid
     */
    public void transitionToPlaying() {
        stateLock.lock();
        try {
            if (currentState != State.READY && currentState != State.PAUSED) {
                throw new IllegalStateException("Cannot start playing from state: " + currentState);
            }
            log.debug("State transition: {} -> PLAYING", currentState);
            currentState = State.PLAYING;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Transition to PAUSED state from PLAYING.
     *
     * @throws IllegalStateException if the transition is invalid
     */
    public void transitionToPaused() {
        stateLock.lock();
        try {
            if (currentState != State.PLAYING) {
                throw new IllegalStateException("Cannot pause from state: " + currentState);
            }
            log.debug("State transition: {} -> PAUSED", currentState);
            currentState = State.PAUSED;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Transition to NO_AUDIO state from READY, LOADING (cancelled), or ERROR.
     *
     * @throws IllegalStateException if the transition is invalid
     */
    public void transitionToNoAudio() {
        stateLock.lock();
        try {
            if (currentState != State.READY
                    && currentState != State.LOADING
                    && currentState != State.ERROR
                    && currentState != State.NO_AUDIO) {
                throw new IllegalStateException("Cannot close audio from state: " + currentState);
            }
            log.debug("State transition: {} -> NO_AUDIO", currentState);
            currentState = State.NO_AUDIO;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Transition to ERROR state from LOADING or PLAYING.
     *
     * @throws IllegalStateException if the transition is invalid
     */
    public void transitionToError() {
        stateLock.lock();
        try {
            if (currentState != State.LOADING && currentState != State.PLAYING) {
                throw new IllegalStateException(
                        "Cannot transition to ERROR from state: " + currentState);
            }
            log.debug("State transition: {} -> ERROR", currentState);
            currentState = State.ERROR;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Execute an action that requires a specific state. Throws if not in the required state.
     *
     * @param requiredState The state required for the action
     * @param action The action to execute
     * @param <T> The return type
     * @return The result of the action
     * @throws IllegalStateException if not in the required state
     */
    public <T> T executeInState(State requiredState, Supplier<T> action) {
        stateLock.lock();
        try {
            checkState(requiredState);
            return action.get();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Execute an action that requires a specific state. Throws if not in the required state.
     *
     * @param requiredState The state required for the action
     * @param action The action to execute
     * @throws IllegalStateException if not in the required state
     */
    public void executeInState(State requiredState, Runnable action) {
        stateLock.lock();
        try {
            checkState(requiredState);
            action.run();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Execute an action with the state lock held, regardless of current state. Useful for
     * operations that need to read state consistently.
     *
     * @param action The action to execute
     * @param <T> The return type
     * @return The result of the action
     */
    public <T> T executeWithLock(Supplier<T> action) {
        stateLock.lock();
        try {
            return action.get();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Check if the state machine is in the expected state.
     *
     * @param expected The expected state
     * @throws IllegalStateException if the current state doesn't match
     */
    public void checkState(State expected) {
        if (currentState != expected) {
            throw new IllegalStateException(
                    String.format(
                            "Operation requires state %s but current state is %s",
                            expected, currentState));
        }
    }

    /**
     * Check if the state machine is in one of the expected states.
     *
     * @param expected The expected states
     * @throws IllegalStateException if the current state doesn't match any expected state
     */
    public void checkStateAny(State... expected) {
        for (State state : expected) {
            if (currentState == state) {
                return;
            }
        }
        throw new IllegalStateException(
                String.format(
                        "Operation requires one of states %s but current state is %s",
                        java.util.Arrays.toString(expected), currentState));
    }

    /**
     * Atomically check if in the expected state and transition to a new state.
     *
     * @param expected The expected current state
     * @param newState The new state to transition to
     * @return true if the transition succeeded, false if current state didn't match expected
     */
    public boolean compareAndSetState(State expected, State newState) {
        stateLock.lock();
        try {
            if (currentState == expected) {
                // Validate the transition is allowed
                if (!isValidTransition(expected, newState)) {
                    log.warn("Invalid transition attempt: {} -> {}", expected, newState);
                    return false;
                }
                log.debug("State transition: {} -> {}", expected, newState);
                currentState = newState;
                return true;
            }
            return false;
        } finally {
            stateLock.unlock();
        }
    }

    /** Reset the state machine to NO_AUDIO. Used for cleanup or error recovery. */
    public void reset() {
        stateLock.lock();
        try {
            State oldState = currentState;
            currentState = State.NO_AUDIO;
            if (oldState != State.NO_AUDIO) {
                log.debug("State reset: {} -> NO_AUDIO", oldState);
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Force transition to ERROR state regardless of current state. This is the "emergency stop" -
     * mark as error regardless of current state.
     */
    public void forceError() {
        stateLock.lock();
        try {
            if (currentState == State.ERROR) {
                return;
            }
            log.warn("Forcing error state from: {}", currentState);
            currentState = State.ERROR;
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
    private boolean isValidTransition(State from, State to) {
        return switch (from) {
            case NO_AUDIO -> to == State.LOADING;
            case LOADING -> to == State.READY || to == State.ERROR || to == State.NO_AUDIO;
            case READY -> to == State.PLAYING || to == State.NO_AUDIO || to == State.LOADING;
            case PLAYING -> to == State.PAUSED || to == State.READY || to == State.ERROR;
            case PAUSED -> to == State.PLAYING || to == State.READY;
            case ERROR -> to == State.NO_AUDIO || to == State.LOADING;
        };
    }
}
