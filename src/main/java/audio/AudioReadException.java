package audio;

import java.io.IOException;
import java.nio.file.Path;
import lombok.Getter;
import lombok.NonNull;

/**
 * Exception thrown when audio sample reading fails.
 *
 * <p>This exception wraps various underlying causes of read failures, such as I/O errors,
 * unsupported formats, or codec failures.
 */
@Getter
public class AudioReadException extends IOException {

    /** The file that failed to read, if known. */
    private final Path audioFile;

    /** The frame position where the read was attempted, if applicable. */
    private final Long startFrame;

    /** The number of frames attempted to read, if applicable. */
    private final Long frameCount;

    /**
     * Creates an exception for a general read failure.
     *
     * @param message Error message
     * @param audioFile The file that failed
     */
    public AudioReadException(@NonNull String message, @NonNull Path audioFile) {
        super(message);
        this.audioFile = audioFile;
        this.startFrame = null;
        this.frameCount = null;
    }

    /**
     * Creates an exception for a read failure at a specific position.
     *
     * @param message Error message
     * @param audioFile The file that failed
     * @param startFrame The frame position where read was attempted
     * @param frameCount The number of frames attempted to read
     */
    public AudioReadException(
            @NonNull String message, @NonNull Path audioFile, long startFrame, long frameCount) {
        super(String.format("%s (at frame %d, count %d)", message, startFrame, frameCount));
        this.audioFile = audioFile;
        this.startFrame = startFrame;
        this.frameCount = frameCount;
    }

    /**
     * Creates an exception wrapping another cause.
     *
     * @param message Error message
     * @param audioFile The file that failed
     * @param cause The underlying cause
     */
    public AudioReadException(
            @NonNull String message, @NonNull Path audioFile, @NonNull Throwable cause) {
        super(message, cause);
        this.audioFile = audioFile;
        this.startFrame = null;
        this.frameCount = null;
    }

    /**
     * Creates an exception for a specific position with a cause.
     *
     * @param message Error message
     * @param audioFile The file that failed
     * @param startFrame The frame position where read was attempted
     * @param frameCount The number of frames attempted to read
     * @param cause The underlying cause
     */
    public AudioReadException(
            @NonNull String message,
            @NonNull Path audioFile,
            long startFrame,
            long frameCount,
            @NonNull Throwable cause) {
        super(String.format("%s (at frame %d, count %d)", message, startFrame, frameCount), cause);
        this.audioFile = audioFile;
        this.startFrame = startFrame;
        this.frameCount = frameCount;
    }

    @Override
    public String getMessage() {
        return String.format(
                "Failed to read audio from %s: %s", audioFile.getFileName(), super.getMessage());
    }
}
