package audio;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.NonNull;

/**
 * Interface for reading audio samples from files for analysis and visualization.
 *
 * <p>This interface is designed for high-performance, parallel reading of audio data, particularly
 * for waveform rendering where multiple segments of the same file need to be read concurrently.
 *
 * <p>Key design principles:
 *
 * <ul>
 *   <li>Thread-safe: All methods must be safe to call from multiple threads
 *   <li>Parallel reads: Multiple read operations on the same file should execute in parallel
 *   <li>File-based: Operates on file paths, not audio handles (decoupled from playback)
 *   <li>Async by default: Returns CompletableFutures for non-blocking operation
 * </ul>
 *
 * <p>Implementation notes:
 *
 * <ul>
 *   <li>Implementations should NOT share file handles between threads
 *   <li>Each read operation should open its own file handle or use thread-local handles
 *   <li>The OS file cache will handle efficiency of reading the same file multiple times
 *   <li>Implementations may use FMOD, native libraries, or pure Java audio libraries
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * SampleReader reader = factory.createReader();
 *
 * // Read multiple segments in parallel
 * var requests = List.of(
 *     new ReadRequest(0, 44100),
 *     new ReadRequest(44100, 44100),
 *     new ReadRequest(88200, 44100)
 * );
 * CompletableFuture<List<AudioData>> segments = reader.readMultiple(path, requests);
 * }</pre>
 */
public interface SampleReader extends Closeable {

    /**
     * Reads audio samples from a file.
     *
     * @param audioFile Path to the audio file
     * @param startFrame Starting frame position (0-based)
     * @param frameCount Number of frames to read
     * @return Future containing the audio data
     * @throws CompletionException wrapping AudioReadException on I/O errors
     */
    @NonNull
    CompletableFuture<AudioData> readSamples(
            @NonNull Path audioFile, long startFrame, long frameCount);

    /**
     * Gets metadata about an audio file without reading samples.
     *
     * <p>This should be a fast operation that just reads file headers.
     *
     * @param audioFile Path to the audio file
     * @return Future containing the audio metadata
     * @throws CompletionException wrapping AudioReadException on I/O errors
     */
    @NonNull
    CompletableFuture<AudioMetadata> getMetadata(@NonNull Path audioFile);

    /**
     * Reads multiple segments from the same file efficiently.
     *
     * <p>Implementations may optimize this for bulk reads, but the default implementation simply
     * reads each segment individually in parallel.
     *
     * @param audioFile Path to the audio file
     * @param requests List of read requests for different segments
     * @return Future containing list of audio data in the same order as requests
     */
    @NonNull
    default CompletableFuture<List<AudioData>> readMultiple(
            @NonNull Path audioFile, @NonNull List<ReadRequest> requests) {

        List<CompletableFuture<AudioData>> futures =
                requests.stream()
                        .map(req -> readSamples(audioFile, req.startFrame(), req.frameCount()))
                        .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(
                        _ ->
                                futures.stream()
                                        .map(CompletableFuture::join)
                                        .collect(Collectors.toList()));
    }

    /** Request for reading a segment of audio data. */
    record ReadRequest(long startFrame, long frameCount) {
        public ReadRequest {
            if (startFrame < 0) {
                throw new IllegalArgumentException("Start frame cannot be negative: " + startFrame);
            }
            if (frameCount < 0) {
                throw new IllegalArgumentException("Frame count cannot be negative: " + frameCount);
            }
        }
    }

    /**
     * Checks if this reader supports a given audio format.
     *
     * @param audioFile Path to check
     * @return true if the file format is supported
     */
    default boolean isFormatSupported(@NonNull Path audioFile) {
        String fileName = audioFile.getFileName().toString().toLowerCase();
        return fileName.endsWith(".wav")
                || fileName.endsWith(".mp3")
                || fileName.endsWith(".flac")
                || fileName.endsWith(".ogg")
                || fileName.endsWith(".aiff")
                || fileName.endsWith(".aac");
    }
}
