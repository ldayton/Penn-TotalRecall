package core.audio.session;

import com.google.errorprone.annotations.Immutable;
import core.audio.AudioHandle;
import core.audio.PlaybackHandle;
import java.io.File;
import java.util.Optional;

/**
 * Immutable context representing the complete state of an audio session. All state mutations
 * produce a new context instance.
 */
@Immutable
public record AudioSessionContext(
        Optional<File> currentFile,
        Optional<AudioHandle> audioHandle,
        Optional<PlaybackHandle> playbackHandle,
        Optional<Long> pendingStartFrame,
        long playheadFrame,
        long totalFrames,
        int sampleRate,
        Optional<String> errorMessage) {

    /** Create an empty initial context. */
    public static AudioSessionContext createInitial() {
        return new AudioSessionContext(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0L,
                0L,
                0,
                Optional.empty());
    }

    /** Apply a command to produce a new context. */
    public AudioSessionContext apply(AudioSessionCommand command) {
        return switch (command) {
            case AudioSessionCommand.LoadFile cmd ->
                    new AudioSessionContext(
                            Optional.of(cmd.file()),
                            Optional.of(cmd.audioHandle()),
                            Optional.empty(),
                            pendingStartFrame,
                            0L,
                            cmd.totalFrames(),
                            cmd.sampleRate(),
                            Optional.empty());

            case AudioSessionCommand.CloseFile cmd ->
                    new AudioSessionContext(
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            0L,
                            0L,
                            0,
                            Optional.empty());

            case AudioSessionCommand.SetLoadError cmd ->
                    new AudioSessionContext(
                            currentFile,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            0L,
                            0L,
                            0,
                            Optional.of(cmd.errorMessage()));

            case AudioSessionCommand.StartPlayback cmd ->
                    new AudioSessionContext(
                            currentFile,
                            audioHandle,
                            Optional.of(cmd.playbackHandle()),
                            Optional.empty(), // Clear pending seek after starting
                            cmd.startFrame(),
                            totalFrames,
                            sampleRate,
                            errorMessage);

            case AudioSessionCommand.PausePlayback cmd ->
                    new AudioSessionContext(
                            currentFile,
                            audioHandle,
                            playbackHandle,
                            pendingStartFrame,
                            cmd.positionFrames(),
                            totalFrames,
                            sampleRate,
                            errorMessage);

            case AudioSessionCommand.ResumePlayback cmd ->
                    new AudioSessionContext(
                            currentFile,
                            audioHandle,
                            playbackHandle,
                            pendingStartFrame,
                            cmd.positionFrames(),
                            totalFrames,
                            sampleRate,
                            errorMessage);

            case AudioSessionCommand.StopPlayback cmd ->
                    new AudioSessionContext(
                            currentFile,
                            audioHandle,
                            Optional.empty(),
                            pendingStartFrame,
                            playheadFrame,
                            totalFrames,
                            sampleRate,
                            errorMessage);

            case AudioSessionCommand.SetPendingSeek cmd ->
                    new AudioSessionContext(
                            currentFile,
                            audioHandle,
                            playbackHandle,
                            Optional.of(cmd.targetFrame()),
                            cmd.targetFrame(),
                            totalFrames,
                            sampleRate,
                            errorMessage);

            case AudioSessionCommand.ClearPendingSeek cmd ->
                    new AudioSessionContext(
                            currentFile,
                            audioHandle,
                            playbackHandle,
                            Optional.empty(),
                            playheadFrame,
                            totalFrames,
                            sampleRate,
                            errorMessage);

            case AudioSessionCommand.UpdatePosition cmd ->
                    new AudioSessionContext(
                            currentFile,
                            audioHandle,
                            playbackHandle,
                            pendingStartFrame,
                            cmd.positionFrames(),
                            totalFrames,
                            sampleRate,
                            errorMessage);
        };
    }

    /** Check if audio is currently loaded. */
    public boolean hasAudio() {
        return audioHandle.isPresent();
    }

    /** Check if playback is active (playing or paused). */
    public boolean hasPlayback() {
        return playbackHandle.isPresent();
    }
}
