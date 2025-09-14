package core.events;

import core.audio.session.AudioSessionStateMachine;
import lombok.NonNull;

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
public record AppStateChangedEvent(
        @NonNull AudioSessionStateMachine.State previousState,
        @NonNull AudioSessionStateMachine.State newState,
        @NonNull Object context) {
    /**
     * Create a state change event with context.
     *
     * @param previousState The state before the transition
     * @param newState The new state after transition
     * @param context Optional context object with additional information
     */
    public AppStateChangedEvent {}

    /**
     * Create a state change event without context.
     *
     * @param previousState The state before the transition
     * @param newState The new state after transition
     */
    public AppStateChangedEvent(
            AudioSessionStateMachine.State previousState, AudioSessionStateMachine.State newState) {
        this(previousState, newState, null);
    }

    /**
     * Check if this is a transition to playing state.
     *
     * @return true if transitioning to PLAYING
     */
    public boolean isTransitionToPlaying() {
        return newState == AudioSessionStateMachine.State.PLAYING;
    }

    /**
     * Check if this is a transition to paused state.
     *
     * @return true if transitioning to PAUSED
     */
    public boolean isTransitionToPaused() {
        return newState == AudioSessionStateMachine.State.PAUSED;
    }

    /**
     * Check if this is a transition to ready state.
     *
     * @return true if transitioning to READY
     */
    public boolean isTransitionToReady() {
        return newState == AudioSessionStateMachine.State.READY;
    }

    /**
     * Check if this is a transition to error state.
     *
     * @return true if transitioning to ERROR
     */
    public boolean isTransitionToError() {
        return newState == AudioSessionStateMachine.State.ERROR;
    }

    /**
     * Check if audio was just loaded.
     *
     * @return true if transitioning from LOADING to READY
     */
    public boolean isAudioLoaded() {
        return previousState == AudioSessionStateMachine.State.LOADING
                && newState == AudioSessionStateMachine.State.READY;
    }

    /**
     * Check if audio was just closed.
     *
     * @return true if transitioning to NO_AUDIO
     */
    public boolean isAudioClosed() {
        return newState == AudioSessionStateMachine.State.NO_AUDIO;
    }

    /**
     * Check if playback just completed naturally.
     *
     * @return true if transitioning from PLAYING to READY with "completed" context
     */
    public boolean isPlaybackCompleted() {
        return previousState == AudioSessionStateMachine.State.PLAYING
                && newState == AudioSessionStateMachine.State.READY
                && "completed".equals(context);
    }

    @Override
    public String toString() {
        return String.format(
                "AppStateChangedEvent[%s -> %s, context=%s]", previousState, newState, context);
    }
}
