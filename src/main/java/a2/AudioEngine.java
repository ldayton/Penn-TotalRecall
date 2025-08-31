package a2;

import app.annotations.ThreadSafe;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;

/**
 * Modern audio engine interface for efficient audio processing and playback. Provides handle-based
 * resource management with explicit lifecycle control.
 */
@ThreadSafe
public interface AudioEngine extends AutoCloseable {

    /** Returns existing handle if already loaded. */
    AudioHandle loadAudio(@NonNull String filePath)
            throws java.io.FileNotFoundException,
                    a2.fmod.exceptions.UnsupportedAudioFormatException,
                    a2.fmod.exceptions.CorruptedAudioFileException;

    /** Non-blocking prefetch for smooth scrolling. */
    CompletableFuture<Void> preloadRange(
            @NonNull AudioHandle handle, long startFrame, long endFrame);

    PlaybackHandle play(@NonNull AudioHandle audio);

    /** Fire-and-forget playback of a range. Cannot be paused/stopped. */
    void playRange(@NonNull AudioHandle audio, long startFrame, long endFrame);

    void pause(@NonNull PlaybackHandle playback);

    void resume(@NonNull PlaybackHandle playback);

    void stop(@NonNull PlaybackHandle playback);

    /** Repositions during active playback. */
    void seek(@NonNull PlaybackHandle playback, long frame);

    PlaybackState getState(@NonNull PlaybackHandle playback);

    long getPosition(@NonNull PlaybackHandle playback);

    boolean isPlaying(@NonNull PlaybackHandle playback);

    CompletableFuture<AudioBuffer> readSamples(
            @NonNull AudioHandle audio, long startFrame, long frameCount);

    AudioMetadata getMetadata(@NonNull AudioHandle audio);

    void addPlaybackListener(@NonNull PlaybackListener listener);

    void removePlaybackListener(@NonNull PlaybackListener listener);

    @Override
    void close();
}
