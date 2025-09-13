package core.audio;

import java.util.Optional;

/**
 * Data source for audio session information. Provides playback state and session status for various
 * components.
 */
public interface AudioSessionDataSource {

    /** Get the current playback position in seconds. Returns empty if not playing/paused. */
    Optional<Double> getPlaybackPosition();

    /** Get the total duration in seconds. Returns empty if no audio is loaded. */
    Optional<Double> getTotalDuration();

    /** Check if audio is currently loaded. */
    boolean isAudioLoaded();

    /** Check if currently playing. */
    boolean isPlaying();

    /** Check if currently loading core.audio. */
    boolean isLoading();

    /** Get any error message to display. */
    Optional<String> getErrorMessage();

    /** Get the current audio handle for waveform rendering. */
    Optional<AudioHandle> getCurrentAudioHandle();

    /** Get the current audio file path. */
    Optional<String> getCurrentAudioFilePath();

    /** Get the sample rate of the current core.audio. */
    Optional<Integer> getSampleRate();

    /** Get the current playback position in frames. Returns empty if not playing/paused. */
    Optional<Long> getPlaybackPositionFrames();

    /** Get the total duration in frames. Returns empty if no audio is loaded. */
    Optional<Long> getTotalFrames();
}
