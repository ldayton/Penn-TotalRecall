package core.audio;

import lombok.NonNull;

/** Listener interface for audio playback events. */
public interface PlaybackListener {

    /**
     * Called periodically during playback with progress updates, using hearing-time seconds derived
     * from the DSP clock (what the listener actually hears).
     *
     * @param playback The playback handle
     * @param hearingSeconds Current position in seconds (hearing-time)
     * @param totalSeconds Total duration in seconds for this playback (hearing-time)
     */
    default void onProgress(
            @NonNull PlaybackHandle playback, double hearingSeconds, double totalSeconds) {}

    /**
     * Called when playback state changes.
     *
     * @param playback The playback handle
     * @param newState The new playback state
     * @param oldState The previous playback state
     */
    default void onStateChanged(
            @NonNull PlaybackHandle playback,
            @NonNull PlaybackState newState,
            @NonNull PlaybackState oldState) {}

    /**
     * Called when playback completes naturally (reaches end).
     *
     * @param playback The playback handle that completed
     */
    default void onPlaybackComplete(@NonNull PlaybackHandle playback) {}

    /**
     * Called when an error occurs during playback.
     *
     * @param playback The playback handle (may be null if error occurs before handle creation)
     * @param error Description of the error
     */
    default void onPlaybackError(PlaybackHandle playback, @NonNull String error) {}
}
