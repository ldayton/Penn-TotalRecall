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
        AudioSessionStateMachine.State machineState,
        Optional<File> currentFile,
        Optional<AudioHandle> audioHandle,
        Optional<PlaybackHandle> playbackHandle,
        Optional<Long> pendingStartFrame,
        long totalFrames,
        int sampleRate,
        Optional<String> errorMessage) {

    /** Create an empty initial context. */
    public static AudioSessionContext createInitial() {
        return new AudioSessionContext(
                AudioSessionStateMachine.State.NO_AUDIO,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0L,
                0,
                Optional.empty());
    }

    /** Apply a command to produce a new context. */
    public AudioSessionContext apply(AudioSessionCommand command) {
        return switch (command) {
            case AudioSessionCommand.LoadFile cmd ->
                    new AudioSessionContext(
                            AudioSessionStateMachine.State.READY,
                            Optional.of(cmd.file()),
                            Optional.of(cmd.audioHandle()),
                            Optional.empty(),
                            pendingStartFrame,
                            cmd.totalFrames(),
                            cmd.sampleRate(),
                            Optional.empty());

            case AudioSessionCommand.CloseFile cmd ->
                    new AudioSessionContext(
                            AudioSessionStateMachine.State.NO_AUDIO,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            0L,
                            0,
                            Optional.empty());

            case AudioSessionCommand.SetLoadError cmd ->
                    new AudioSessionContext(
                            AudioSessionStateMachine.State.ERROR,
                            currentFile,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            0L,
                            0,
                            Optional.of(cmd.errorMessage()));

            case AudioSessionCommand.StartPlayback cmd ->
                    new AudioSessionContext(
                            AudioSessionStateMachine.State.PLAYING,
                            currentFile,
                            audioHandle,
                            Optional.of(cmd.playbackHandle()),
                            Optional.empty(), // Clear pending seek after starting
                            totalFrames,
                            sampleRate,
                            errorMessage);

            case AudioSessionCommand.PausePlayback cmd ->
                    new AudioSessionContext(
                            AudioSessionStateMachine.State.PAUSED,
                            currentFile,
                            audioHandle,
                            playbackHandle,
                            pendingStartFrame,
                            totalFrames,
                            sampleRate,
                            errorMessage);

            case AudioSessionCommand.ResumePlayback cmd ->
                    new AudioSessionContext(
                            AudioSessionStateMachine.State.PLAYING,
                            currentFile,
                            audioHandle,
                            playbackHandle,
                            pendingStartFrame,
                            totalFrames,
                            sampleRate,
                            errorMessage);

            case AudioSessionCommand.StopPlayback cmd ->
                    new AudioSessionContext(
                            AudioSessionStateMachine.State.READY,
                            currentFile,
                            audioHandle,
                            Optional.empty(),
                            pendingStartFrame,
                            totalFrames,
                            sampleRate,
                            errorMessage);

            case AudioSessionCommand.SetPendingSeek cmd ->
                    new AudioSessionContext(
                            machineState,
                            currentFile,
                            audioHandle,
                            playbackHandle,
                            Optional.of(cmd.targetFrame()),
                            totalFrames,
                            sampleRate,
                            errorMessage);

            case AudioSessionCommand.ClearPendingSeek cmd ->
                    new AudioSessionContext(
                            machineState,
                            currentFile,
                            audioHandle,
                            playbackHandle,
                            Optional.empty(),
                            totalFrames,
                            sampleRate,
                            errorMessage);

            case AudioSessionCommand.UpdatePosition cmd ->
                    // Position updates don't change context state
                    this;
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
