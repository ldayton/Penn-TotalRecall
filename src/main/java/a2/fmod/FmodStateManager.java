package a2.fmod;

import a2.exceptions.AudioEngineException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the state lifecycle and thread-safe transitions for the FMOD audio engine. Ensures all
 * state changes are atomic and properly synchronized.
 */
@Slf4j
class FmodStateManager {

    enum State {
        UNINITIALIZED,
        INITIALIZING,
        INITIALIZED,
        CLOSING,
        CLOSED
    }

    private final ReentrantLock stateLock = new ReentrantLock();
    private volatile State currentState = State.UNINITIALIZED;

    /** Get the current state. */
    State getCurrentState() {
        return currentState;
    }

    /** Check if the engine is in a running state (initialized and not closing/closed). */
    boolean isRunning() {
        State state = currentState;
        return state == State.INITIALIZED;
    }

    /**
     * Transition to a new state, executing the provided action. The action is executed while
     * holding the state lock.
     */
    void transitionTo(State newState, Runnable action) {
        stateLock.lock();
        try {
            validateTransition(currentState, newState);
            State oldState = currentState;
            currentState = newState;
            log.debug("State transition: {} -> {}", oldState, newState);

            try {
                action.run();
            } catch (Exception e) {
                // Rollback state on failure
                currentState = oldState;
                throw e;
            }
        } finally {
            stateLock.unlock();
        }
    }

    /** Execute an action that requires a specific state. Throws if not in the required state. */
    <T> T executeInState(State requiredState, Supplier<T> action) {
        stateLock.lock();
        try {
            checkState(requiredState);
            return action.get();
        } finally {
            stateLock.unlock();
        }
    }

    /** Execute an action that requires a specific state. Throws if not in the required state. */
    void executeInState(State requiredState, Runnable action) {
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
     */
    <T> T executeWithLock(Supplier<T> action) {
        stateLock.lock();
        try {
            return action.get();
        } finally {
            stateLock.unlock();
        }
    }

    /** Check if currently in the specified state, throw if not. */
    void checkState(State expected) {
        if (currentState != expected) {
            throw new AudioEngineException(
                    String.format(
                            "Operation requires state %s but current state is %s",
                            expected, currentState));
        }
    }

    /** Check if currently in one of the specified states, throw if not. */
    void checkStateAny(State... expected) {
        for (State state : expected) {
            if (currentState == state) {
                return;
            }
        }
        throw new AudioEngineException(
                String.format(
                        "Operation requires one of states %s but current state is %s",
                        java.util.Arrays.toString(expected), currentState));
    }

    /** Validate that a state transition is legal. */
    private void validateTransition(State from, State to) {
        boolean valid =
                switch (from) {
                    case UNINITIALIZED -> to == State.INITIALIZING;
                    case INITIALIZING -> to == State.INITIALIZED || to == State.CLOSED;
                    case INITIALIZED -> to == State.CLOSING;
                    case CLOSING -> to == State.CLOSED;
                    case CLOSED -> to == State.INITIALIZING; // Allow re-initialization
                    default -> false;
                };

        if (!valid) {
            throw new AudioEngineException(
                    String.format("Invalid state transition from %s to %s", from, to));
        }
    }

    /**
     * Atomically compare and set the state. Returns true if successful, false if current state
     * doesn't match expected.
     */
    boolean compareAndSetState(State expected, State newState) {
        stateLock.lock();
        try {
            if (currentState == expected) {
                validateTransition(currentState, newState);
                currentState = newState;
                log.debug("State transition: {} -> {}", expected, newState);
                return true;
            }
            return false;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Get the state lock for operations that need custom locking. Use sparingly - prefer the
     * execute methods.
     */
    ReentrantLock getStateLock() {
        return stateLock;
    }
}
