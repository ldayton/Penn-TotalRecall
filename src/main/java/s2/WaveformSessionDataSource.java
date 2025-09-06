package s2;

import a2.AudioHandle;
import java.util.Optional;

/**
 * Data source for waveform rendering from the audio session. Provides playback state and session
 * status for waveform display.
 */
public interface WaveformSessionDataSource {

    /** Get the current playback position in seconds. Returns empty if not playing/paused. */
    Optional<Double> getPlaybackPosition();

    /** Get the total duration in seconds. Returns empty if no audio is loaded. */
    Optional<Double> getTotalDuration();

    /** Check if audio is currently loaded. */
    boolean isAudioLoaded();

    /** Check if currently playing. */
    boolean isPlaying();

    /** Check if currently loading audio. */
    boolean isLoading();

    /** Get any error message to display. */
    Optional<String> getErrorMessage();

    /** Get the current audio handle for waveform rendering. */
    Optional<AudioHandle> getCurrentAudioHandle();

    /** Get the current audio file path. */
    Optional<String> getCurrentAudioFilePath();

    /** Get the sample rate of the current audio. */
    Optional<Integer> getSampleRate();
}
