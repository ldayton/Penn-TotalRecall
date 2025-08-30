package audio;

import events.AudioEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-level audio player for scientific annotation with dual playback modes.
 *
 * <h3>Threading Model</h3>
 *
 * <ul>
 *   <li>Single PlaybackThread per player instance
 *   <li>Event notifications via thread pool (non-blocking)
 *   <li>Progress callbacks in playback thread (lightweight, 30fps target)
 *   <li>Thread-safe listener management via CopyOnWriteArrayList
 *   <li>AtomicReference for playback thread lifecycle
 *   <li>Status updates synchronized on stateLock to prevent race conditions
 * </ul>
 *
 * <h3>Playback Modes</h3>
 *
 * <ul>
 *   <li>MAIN_PLAYBACK: Full control, status updates, progress callbacks, stoppable
 *   <li>SHORT_INTERVAL: Fire-and-forget preview, no status change, not user-stoppable
 *   <li>Main playback blocks short intervals; short intervals respect active main playback
 *   <li>New main playback interrupts existing short intervals
 * </ul>
 *
 * <h3>Format Support</h3>
 *
 * <ul>
 *   <li>All formats supported by FMOD Core (WAV, AIFF, AU, OGG, MP3, FLAC, etc.)
 *   <li>Any sample rate (FMOD handles resampling automatically)
 *   <li>Any channel configuration (mono, stereo, multi-channel)
 *   <li>Any bit depth (8-bit, 16-bit, 24-bit, 32-bit, float)
 *   <li>Format validation and error handling done by FMOD Core
 * </ul>
 *
 * <h3>State Management</h3>
 *
 * <ul>
 *   <li>BUSY: Not ready (before open())
 *   <li>READY: File loaded, ready to play
 *   <li>PLAYING: Main playback active
 *   <li>Status transitions: BUSY → READY → PLAYING → READY
 * </ul>
 *
 * <h3>Behavioral Rules</h3>
 *
 * <ul>
 *   <li>playAt() ignored if already PLAYING (no interruption)
 *   <li>playShortInterval() ignored if PLAYING (main takes precedence)
 *   <li>Short intervals limited to 1MB (1,048,576 frames)
 *   <li>stop() is synchronous, returns exact hearing frame
 *   <li>Frame addressing is zero-based PCM samples
 * </ul>
 */
public class AudioPlayer {
    private static final Logger logger = LoggerFactory.getLogger(AudioPlayer.class);

    // Performance and timing constants
    private static final int POLLING_SLEEP_MS = 10;
    private static final int PROGRESS_UPDATES_PER_SECOND = 30;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    // Memory and size limits
    private static final long SHORT_INTERVAL_MAX_FRAMES = 1_048_576L; // 1MB limit

    // Thread naming
    private static final String EVENT_THREAD_NAME = "AudioEvent";
    private static final String PLAYBACK_THREAD_PREFIX = "FMOD-Playback-";

    public enum Status {
        BUSY, // Player not ready for playback
        READY, // Player ready for playback
        PLAYING // Main playback in progress
    }

    private enum PlaybackMode {
        MAIN_PLAYBACK, // Full playback with listeners and status updates
        SHORT_INTERVAL // Fire-and-forget preview playback
    }

    /** Immutable playback parameters passed to PlaybackThread. */
    private record PlaybackRequest(
            @NonNull String fileName, long startFrame, long endFrame, @NonNull PlaybackMode mode) {}

    private final FmodCore fmodCore;
    private final List<AudioEvent.Listener> listeners = new CopyOnWriteArrayList<>();
    private final Object stateLock = new Object();

    // Player state
    private volatile Status status = Status.BUSY;
    private volatile String currentFileName;
    private volatile long totalFrames;

    // Playback thread state
    private final AtomicReference<PlaybackThread> currentThread = new AtomicReference<>();

    // Event handling thread pool
    private final ExecutorService eventExecutor =
            Executors.newCachedThreadPool(
                    r -> {
                        Thread t = new Thread(r, EVENT_THREAD_NAME);
                        t.setDaemon(true);
                        return t;
                    });

    // Audio format validation - sample rate obtained from FMOD when needed

    public AudioPlayer(@NonNull FmodCore fmodCore) {
        this.fmodCore = fmodCore;
    }

    /**
     * Shuts down the event thread pool and waits for completion. Should be called when the
     * AudioPlayer is no longer needed.
     */
    public void shutdown() {
        if (eventExecutor != null && !eventExecutor.isShutdown()) {
            eventExecutor.shutdown();
            try {
                // Wait up to 5 seconds for threads to complete
                if (!eventExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    logger.warn(
                            "Event executor did not terminate within {} seconds, forcing shutdown",
                            SHUTDOWN_TIMEOUT_SECONDS);
                    eventExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for event executor shutdown", e);
                eventExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Opens audio file for playback.
     *
     * @param fileName Absolute or relative path to audio file
     * @throws FileNotFoundException if file does not exist
     * @implNote Thread-safe: synchronized on stateLock
     * @implNote Status transition: * → READY
     * @implNote FMOD Core handles format detection and validation
     */
    public void open(@NonNull String fileName) throws FileNotFoundException {
        synchronized (stateLock) {
            File audioFile = new File(fileName);
            if (!audioFile.exists()) {
                throw new FileNotFoundException("Audio file not found: " + fileName);
            }

            this.currentFileName = audioFile.getAbsolutePath();
            this.totalFrames = -1; // Unknown until FMOD loads it

            logger.debug("Opened audio file: {}", fileName);

            status = Status.READY;
            fireEvent(AudioEvent.Type.OPENED, -1, null);
        }
    }

    public void playAt(long frame) throws IllegalArgumentException {
        if (totalFrames > 0) {
            playAt(frame, totalFrames - 1);
        } else {
            // Total frames unknown, use large but safe end frame
            playAt(frame, Long.MAX_VALUE);
        }
    }

    /**
     * Starts main playback from startFrame to endFrame.
     *
     * @param startFrame Zero-based PCM sample to start playback
     * @param endFrame Zero-based PCM sample to stop playback (exclusive)
     * @throws IllegalArgumentException if frame range is invalid
     * @implNote Ignored if status is BUSY, not opened, or already PLAYING
     * @implNote Status transition: READY → PLAYING
     * @implNote Thread-safe: synchronized on stateLock
     * @implNote Fires PLAYING event, sends progress callbacks
     */
    public void playAt(long startFrame, long endFrame) throws IllegalArgumentException {
        validateFrameRange(startFrame, endFrame);

        synchronized (stateLock) {
            if (status == Status.BUSY || currentFileName == null) {
                return; // No effect if not ready
            }

            if (status == Status.PLAYING) {
                return; // No effect if already playing
            }

            startPlayback(currentFileName, startFrame, endFrame, PlaybackMode.MAIN_PLAYBACK);
        }
    }

    /**
     * Starts fire-and-forget preview playback.
     *
     * @param startFrame Zero-based PCM sample to start playback
     * @param endFrame Zero-based PCM sample to stop playback (exclusive)
     * @throws IllegalArgumentException if frame range invalid or >1MB
     * @implNote Ignored if status BUSY, not opened, or main playback active
     * @implNote No status change, no progress callbacks, not user-stoppable
     * @implNote Limited to 1,048,576 frames to prevent memory issues
     * @implNote Thread-safe: synchronized on stateLock
     */
    public void playShortInterval(long startFrame, long endFrame) throws IllegalArgumentException {
        validateFrameRange(startFrame, endFrame);

        // Enforce 1MB limit for short intervals to avoid memory issues
        if (endFrame - startFrame > SHORT_INTERVAL_MAX_FRAMES) {
            throw new IllegalArgumentException(
                    "Short interval too long: "
                            + (endFrame - startFrame)
                            + " frames (max "
                            + SHORT_INTERVAL_MAX_FRAMES
                            + ")");
        }

        synchronized (stateLock) {
            if (status == Status.BUSY || currentFileName == null) {
                return; // No effect if not ready
            }

            if (status == Status.PLAYING) {
                return; // No effect if main playback active - main playback takes precedence
            }

            startPlayback(currentFileName, startFrame, endFrame, PlaybackMode.SHORT_INTERVAL);
        }
    }

    /**
     * Stops main playback synchronously and returns hearing frame.
     *
     * @return Last heard PCM frame (absolute position), or -1 if not playing
     * @implNote Synchronous: blocks until playback fully stopped
     * @implNote Only affects main playback, not short intervals
     * @implNote Status transition: PLAYING → READY
     * @implNote Thread-safe: synchronized on stateLock
     * @implNote Fires STOPPED event with hearing frame
     */
    public long stop() {
        PlaybackThread thread = currentThread.get();
        if (thread == null) {
            return -1;
        }

        synchronized (stateLock) {
            if (status != Status.PLAYING) {
                return -1;
            }

            // Synchronous stop as required by contract
            long hearingFrame = thread.stopAndWait();

            status = Status.READY;
            fireEvent(AudioEvent.Type.STOPPED, hearingFrame, null);

            return hearingFrame;
        }
    }

    public void addListener(@NonNull AudioEvent.Listener listener) {
        listeners.add(listener);
    }

    /**
     * Gets the current playback status.
     *
     * @return Current playback status
     */
    public Status getStatus() {
        return status;
    }

    private void validateFrameRange(long startFrame, long endFrame)
            throws IllegalArgumentException {
        if (startFrame < 0) {
            throw new IllegalArgumentException("Start frame cannot be negative: " + startFrame);
        }
        if (endFrame <= startFrame) {
            throw new IllegalArgumentException(
                    "End frame must be greater than start frame: "
                            + endFrame
                            + " <= "
                            + startFrame);
        }
        // Skip file length validation - FMOD will handle out-of-bounds frames
    }

    private void startPlayback(
            @NonNull String fileName, long startFrame, long endFrame, @NonNull PlaybackMode mode) {
        // Stop any existing playback
        PlaybackThread oldThread = currentThread.get();
        if (oldThread != null) {
            oldThread.requestStop();
        }

        // Create new playback thread
        PlaybackRequest request = new PlaybackRequest(fileName, startFrame, endFrame, mode);
        PlaybackThread newThread = new PlaybackThread(request);

        if (currentThread.compareAndSet(oldThread, newThread)) {
            // Update status BEFORE starting thread to prevent race condition
            boolean isMainPlayback = (mode == PlaybackMode.MAIN_PLAYBACK);
            if (isMainPlayback) {
                status = Status.PLAYING;
            }

            newThread.start();
            fireEvent(AudioEvent.Type.PLAYING, startFrame, null);
        }
    }

    private void fireEvent(@NonNull AudioEvent.Type type, long frame, String errorMessage) {
        if (listeners.isEmpty()) {
            return;
        }

        AudioEvent event = new AudioEvent(type, frame, errorMessage);

        // Use thread pool instead of creating new threads for each event
        eventExecutor.submit(
                () -> {
                    for (AudioEvent.Listener listener : listeners) {
                        try {
                            listener.onEvent(event);
                        } catch (Exception e) {
                            logger.warn("Error in listener callback", e);
                        }
                    }
                });
    }

    private void fireProgressUpdate(long frame) {
        if (listeners.isEmpty()) {
            return;
        }

        // Execute directly in playback thread for better performance
        // Progress callbacks should be lightweight and non-blocking
        for (AudioEvent.Listener listener : listeners) {
            try {
                listener.onProgress(frame);
            } catch (Exception e) {
                logger.warn("Error in progress callback", e);
            }
        }
    }

    /**
     * Single background thread managing FMOD playback lifecycle.
     *
     * <h4>Threading Details:</h4>
     *
     * <ul>
     *   <li>Daemon thread, does not prevent JVM shutdown
     *   <li>10ms polling loop for position updates
     *   <li>Progress callbacks executed directly (~30fps for main playback)
     *   <li>Automatic cleanup on completion or interruption
     *   <li>Volatile fields for cross-thread communication
     *   <li>Status updates synchronized to prevent race conditions
     * </ul>
     */
    private final class PlaybackThread extends Thread {
        private final PlaybackRequest request;
        private volatile boolean stopRequested = false;
        private final AtomicLong lastHearingFrame = new AtomicLong(-1);

        PlaybackThread(@NonNull PlaybackRequest request) {
            super(PLAYBACK_THREAD_PREFIX + request.mode);
            this.request = request;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                runPlayback();
            } catch (Exception e) {
                logger.error("Error in playback thread", e);
                fireEvent(AudioEvent.Type.ERROR, -1, e.getMessage());
            } finally {
                // Always clean up thread reference and status, regardless of how we exit
                if (request.mode == PlaybackMode.MAIN_PLAYBACK) {
                    synchronized (stateLock) {
                        if (status == Status.PLAYING) {
                            status = Status.READY;
                        }
                    }
                }

                // Clean up thread reference last
                currentThread.compareAndSet(this, null);
            }
        }

        private void runPlayback() {
            // Start FMOD playback
            int result =
                    fmodCore.startPlayback(request.fileName, request.startFrame, request.endFrame);
            if (result != 0) {
                logger.error("FMOD playback failed with error code: {}", result);
                fireEvent(AudioEvent.Type.ERROR, -1, "FMOD playback failed: " + result);
                return;
            }

            // Progress tracking loop (only for main playback)
            if (request.mode == PlaybackMode.MAIN_PLAYBACK) {
                runMainPlaybackLoop();
            } else {
                runShortIntervalLoop();
            }
        }

        private void runMainPlaybackLoop() {
            long lastProgressFrame = -1;

            // Get actual sample rate from FMOD - fail fast if not available
            int currentSampleRate;
            try {
                currentSampleRate = fmodCore.getSampleRate();
            } catch (IllegalStateException e) {
                logger.error("Cannot determine sample rate for progress updates", e);
                fireEvent(AudioEvent.Type.ERROR, -1, "Cannot determine audio file sample rate");
                // Don't return - let the finally block handle cleanup
                return;
            }

            long progressInterval = currentSampleRate / PROGRESS_UPDATES_PER_SECOND;

            long lastLatencyLogMs = 0;

            while (!stopRequested && fmodCore.playbackInProgress()) {
                long currentFrame = fmodCore.streamPosition();
                lastHearingFrame.set(request.startFrame + currentFrame);

                // Send progress updates at ~30fps
                if (currentFrame - lastProgressFrame >= progressInterval
                        || lastProgressFrame == -1) {
                    fireProgressUpdate(lastHearingFrame.get());
                    lastProgressFrame = currentFrame;
                }

                // Debug: log measured output latency once per second
                if (logger.isDebugEnabled()) {
                    long now = System.currentTimeMillis();
                    if (now - lastLatencyLogMs >= 1000) {
                        long latencyMs = fmodCore.getMeasuredLatencyMillis();
                        audio.FmodCore.LatencyInfo info = fmodCore.getLatencyInfo();
                        if (latencyMs >= 0) {
                            if (info != null) {
                                logger.debug(
                                        "FMOD latency ~{} ms (bufLen={}, numBuf={}, outRate={})",
                                        latencyMs,
                                        info.bufferLength,
                                        info.numBuffers,
                                        info.outputRate);
                            } else {
                                logger.debug("FMOD latency ~{} ms", latencyMs);
                            }
                        }
                        lastLatencyLogMs = now;
                    }
                }

                try {
                    Thread.sleep(POLLING_SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Handle end-of-media
            if (!stopRequested && !fmodCore.playbackInProgress()) {
                lastHearingFrame.set(request.endFrame);
                fireEvent(AudioEvent.Type.EOM, lastHearingFrame.get(), null);
            }
        }

        private void runShortIntervalLoop() {
            // Just wait for completion, no progress updates
            while (!stopRequested && fmodCore.playbackInProgress()) {
                try {
                    Thread.sleep(POLLING_SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            long position = fmodCore.streamPosition();
            if (position >= 0) {
                lastHearingFrame.set(request.startFrame + position);
            }
        }

        /** Requests asynchronous thread termination. */
        void requestStop() {
            stopRequested = true;
            interrupt(); // Wake up from sleep
        }

        /**
         * Stops FMOD playback synchronously and waits for thread completion.
         *
         * @return Last hearing frame (absolute position)
         * @implNote Blocks up to 1 second for thread termination
         */
        long stopAndWait() {
            requestStop();

            // Stop FMOD playback immediately
            long fmodPosition = fmodCore.stopPlayback();
            if (fmodPosition >= 0) {
                lastHearingFrame.set(request.startFrame + fmodPosition);
            }

            // Wait for thread to finish (should be quick since FMOD is already stopped)
            try {
                join(1000); // 1 second timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return lastHearingFrame.get();
        }
    }
}
