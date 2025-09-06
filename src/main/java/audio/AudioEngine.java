package a2;

import a2.exceptions.AudioLoadException;
import app.annotations.ThreadSafe;
import lombok.NonNull;

/**
 * Modern audio engine interface for efficient audio processing and playback. Provides handle-based
 * resource management with explicit lifecycle control.
 */
@ThreadSafe
public interface AudioEngine extends AutoCloseable {

    /** Returns existing handle if already loaded. */
    AudioHandle loadAudio(@NonNull String filePath) throws AudioLoadException;

    PlaybackHandle play(@NonNull AudioHandle audio);

    PlaybackHandle play(@NonNull AudioHandle audio, long startFrame, long endFrame);

    void pause(@NonNull PlaybackHandle playback);

    void resume(@NonNull PlaybackHandle playback);

    void stop(@NonNull PlaybackHandle playback);

    /** Repositions during active playback. */
    void seek(@NonNull PlaybackHandle playback, long frame);

    PlaybackState getState(@NonNull PlaybackHandle playback);

    long getPosition(@NonNull PlaybackHandle playback);

    boolean isPlaying(@NonNull PlaybackHandle playback);

    boolean isPaused(@NonNull PlaybackHandle playback);

    boolean isStopped(@NonNull PlaybackHandle playback);

    AudioMetadata getMetadata(@NonNull AudioHandle audio);

    void addPlaybackListener(@NonNull PlaybackListener listener);

    void removePlaybackListener(@NonNull PlaybackListener listener);

    @Override
    void close();
}
