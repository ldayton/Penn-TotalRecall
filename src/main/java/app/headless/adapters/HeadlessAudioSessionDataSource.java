package app.headless.adapters;

import core.audio.AudioHandle;
import core.audio.session.AudioSessionDataSource;
import java.util.Optional;

/**
 * Headless implementation of AudioSessionDataSource that returns empty/default results. Used for
 * testing and headless environments where waveform data is not available.
 */
public class HeadlessAudioSessionDataSource implements AudioSessionDataSource {

    @Override
    public Optional<AudioHandle> getCurrentAudioHandle() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getCurrentAudioFilePath() {
        return Optional.empty();
    }

    @Override
    public AudioSessionSnapshot snapshot() {
        return new AudioSessionSnapshot(
                core.audio.session.AudioSessionStateMachine.State.NO_AUDIO,
                0L,
                0L,
                0,
                Optional.empty());
    }
}
