package core.audio.session;

import core.audio.AudioHandle;
import java.util.Optional;

/**
 * Data source for audio session information. Provides playback state and session status for various
 * components.
 */
public interface AudioSessionDataSource {

    /** Get the current audio handle for waveform rendering. */
    Optional<AudioHandle> getCurrentAudioHandle();

    /** Get the current audio file path. */
    Optional<String> getCurrentAudioFilePath();

    /** Minimal snapshot of audio timeline state for projection. */
    record AudioSessionSnapshot(
            AudioSessionStateMachine.State state,
            long totalFrames,
            long playheadFrame,
            int sampleRate,
            Optional<String> errorMessage) {}

    /** Build and return a minimal, frame-based snapshot of the current audio session. */
    AudioSessionSnapshot snapshot();
}
