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
    public Optional<Double> getPlaybackPosition() {
        return Optional.empty();
    }

    @Override
    public Optional<Double> getTotalDuration() {
        return Optional.empty();
    }

    @Override
    public boolean isAudioLoaded() {
        return false;
    }

    @Override
    public boolean isPlaying() {
        return false;
    }

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public Optional<String> getErrorMessage() {
        return Optional.empty();
    }

    @Override
    public Optional<AudioHandle> getCurrentAudioHandle() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getCurrentAudioFilePath() {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> getSampleRate() {
        return Optional.empty();
    }

    @Override
    public Optional<Long> getPlaybackPositionFrames() {
        return Optional.empty();
    }

    @Override
    public Optional<Long> getTotalFrames() {
        return Optional.empty();
    }
}
