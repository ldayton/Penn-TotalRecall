package events;

import lombok.Getter;
import state.AppStateManager;

/**
 * Event fired when the application state changes. This is the primary event for communicating state
 * transitions throughout the application.
 *
 * <p>The context object provides additional information about the state change:
 *
 * <ul>
 *   <li>For LOADING: The AudioFile being loaded
 *   <li>For READY: The AudioFile that was loaded
 *   <li>For PLAYING: The start frame position (Long)
 *   <li>For PAUSED: The paused frame position (Long)
 *   <li>For ERROR: The exception that caused the error
 *   <li>For NO_AUDIO: The file that was closed (if any)
 * </ul>
 */
@Getter
public class AppStateChangedEvent {
    private final AppStateManager.State previousState;
    private final AppStateManager.State newState;
    private final Object context;

    /**
     * Create a state change event with context.
     *
     * @param previousState The state before the transition
     * @param newState The new state after transition
     * @param context Optional context object with additional information
     */
    public AppStateChangedEvent(
            AppStateManager.State previousState, AppStateManager.State newState, Object context) {
        this.previousState = previousState;
        this.newState = newState;
        this.context = context;
    }

    /**
     * Create a state change event without context.
     *
     * @param previousState The state before the transition
     * @param newState The new state after transition
     */
    public AppStateChangedEvent(
            AppStateManager.State previousState, AppStateManager.State newState) {
        this(previousState, newState, null);
    }

    /**
     * Check if this is a transition to playing state.
     *
     * @return true if transitioning to PLAYING
     */
    public boolean isTransitionToPlaying() {
        return newState == AppStateManager.State.PLAYING;
    }

    /**
     * Check if this is a transition to paused state.
     *
     * @return true if transitioning to PAUSED
     */
    public boolean isTransitionToPaused() {
        return newState == AppStateManager.State.PAUSED;
    }

    /**
     * Check if this is a transition to ready state.
     *
     * @return true if transitioning to READY
     */
    public boolean isTransitionToReady() {
        return newState == AppStateManager.State.READY;
    }

    /**
     * Check if this is a transition to error state.
     *
     * @return true if transitioning to ERROR
     */
    public boolean isTransitionToError() {
        return newState == AppStateManager.State.ERROR;
    }

    /**
     * Check if audio was just loaded.
     *
     * @return true if transitioning from LOADING to READY
     */
    public boolean isAudioLoaded() {
        return previousState == AppStateManager.State.LOADING
                && newState == AppStateManager.State.READY;
    }

    /**
     * Check if audio was just closed.
     *
     * @return true if transitioning to NO_AUDIO
     */
    public boolean isAudioClosed() {
        return newState == AppStateManager.State.NO_AUDIO;
    }

    /**
     * Check if playback just completed naturally.
     *
     * @return true if transitioning from PLAYING to READY with "completed" context
     */
    public boolean isPlaybackCompleted() {
        return previousState == AppStateManager.State.PLAYING
                && newState == AppStateManager.State.READY
                && "completed".equals(context);
    }

    @Override
    public String toString() {
        return String.format(
                "AppStateChangedEvent[%s -> %s, context=%s]", previousState, newState, context);
    }
}
