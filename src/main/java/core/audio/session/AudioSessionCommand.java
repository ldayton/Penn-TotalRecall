package core.audio.session;

import com.google.errorprone.annotations.Immutable;
import core.audio.AudioHandle;
import core.audio.PlaybackHandle;
import java.io.File;

/**
 * Commands that mutate audio session state. Each command represents a specific state change to be
 * applied atomically. All commands are immutable value objects.
 */
@Immutable
public sealed interface AudioSessionCommand {

    // File operations
    record LoadFile(File file, AudioHandle audioHandle, long totalFrames, int sampleRate)
            implements AudioSessionCommand {}

    record CloseFile() implements AudioSessionCommand {}

    record SetLoadError(String errorMessage) implements AudioSessionCommand {}

    // Playback control
    record StartPlayback(PlaybackHandle playbackHandle, long startFrame)
            implements AudioSessionCommand {}

    record PausePlayback(long positionFrames) implements AudioSessionCommand {}

    record ResumePlayback(long positionFrames) implements AudioSessionCommand {}

    record StopPlayback() implements AudioSessionCommand {}

    // Seek operations
    record SetPendingSeek(long targetFrame) implements AudioSessionCommand {}

    record ClearPendingSeek() implements AudioSessionCommand {}

    record UpdatePosition(long positionFrames) implements AudioSessionCommand {}
}
