package audio.fmod;

import app.annotations.ThreadSafe;
import audio.PlaybackHandle;
import audio.PlaybackListener;
import audio.PlaybackState;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages playback event listeners and progress monitoring for the FMOD audio engine. Handles
 * listener registration, progress callbacks, and state change notifications.
 *
 * <p>This manager is responsible for:
 *
 * <ul>
 *   <li>Managing PlaybackListener subscriptions
 *   <li>Running periodic progress updates for active playback
 *   <li>Notifying listeners of state changes
 *   <li>Detecting and notifying playback completion
 * </ul>
 *
 * <p>Thread Safety: All listener notifications are executed on a dedicated timer thread to avoid
 * blocking audio operations. Listener registration is thread-safe.
 */
@ThreadSafe
@Slf4j
class FmodListenerManager {

    private static final long DEFAULT_PROGRESS_INTERVAL_MS = 100;

    private final long progressIntervalMs;
    private final FmodLibrary fmod;
    private final Pointer system; // For latency calculations
    private ScheduledExecutorService progressTimer;

    // Current monitoring state
    private volatile FmodPlaybackHandle currentHandle;
    private volatile long totalFrames;

    // Listener management
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    /**
     * Creates a new listener manager with default progress interval.
     *
     * @param fmod The FMOD library interface for position queries
     * @param system The FMOD system pointer for latency calculations (can be null)
     */
    FmodListenerManager(@NonNull FmodLibrary fmod, Pointer system) {
        this(fmod, system, DEFAULT_PROGRESS_INTERVAL_MS);
    }

    /**
     * Creates a new listener manager with custom progress interval.
     *
     * @param fmod The FMOD library interface for position queries
     * @param system The FMOD system pointer for latency calculations (can be null)
     * @param progressIntervalMs Interval between progress updates in milliseconds
     */
    FmodListenerManager(@NonNull FmodLibrary fmod, Pointer system, long progressIntervalMs) {
        this.fmod = fmod;
        this.system = system;
        this.progressIntervalMs = progressIntervalMs;
    }

    /**
     * Add a playback listener to receive notifications.
     *
     * @param listener The listener to add
     */
    void addListener(@NonNull PlaybackListener listener) {
        if (isShutdown.get()) {
            log.warn("Cannot add listener to shutdown manager");
            return;
        }
        listeners.add(listener);
        log.trace("Added listener: {}", listener);
    }

    /**
     * Remove a playback listener.
     *
     * @param listener The listener to remove
     */
    void removeListener(@NonNull PlaybackListener listener) {
        listeners.remove(listener);
        log.trace("Removed listener: {}", listener);
    }

    /**
     * Start monitoring progress for the given playback handle. This will begin periodic progress
     * callbacks to all registered listeners. Only one playback can be monitored at a time - calling
     * this stops monitoring any previous playback.
     *
     * @param handle The playback handle to monitor
     * @param totalFrames The total duration in frames for progress calculations
     */
    void startMonitoring(@NonNull FmodPlaybackHandle handle, long totalFrames) {
        if (isShutdown.get()) {
            log.warn("Cannot start monitoring on shutdown manager");
            return;
        }

        // Stop any existing monitoring
        stopMonitoring();

        // Store the new handle and duration
        this.currentHandle = handle;
        this.totalFrames = totalFrames;

        // Start progress timer if we have listeners
        if (!listeners.isEmpty()) {
            progressTimer =
                    Executors.newSingleThreadScheduledExecutor(
                            r -> {
                                Thread t = new Thread(r, "FmodProgressTimer");
                                t.setDaemon(true);
                                return t;
                            });

            progressTimer.scheduleAtFixedRate(
                    this::updateProgress,
                    0, // Start immediately to capture initial position
                    progressIntervalMs,
                    TimeUnit.MILLISECONDS);

        } else {
        }
    }

    /** Stop monitoring the current playback. Progress callbacks will cease after this call. */
    void stopMonitoring() {
        currentHandle = null;
        totalFrames = 0;

        ScheduledExecutorService timer = progressTimer;
        if (timer != null) {
            progressTimer = null;
            timer.shutdown();
            try {
                if (!timer.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                    timer.shutdownNow();
                }
            } catch (InterruptedException e) {
                timer.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Notify listeners of a state change. This is called immediately when state changes occur.
     *
     * @param handle The playback handle
     * @param newState The new state
     * @param oldState The previous state
     */
    void notifyStateChanged(
            @NonNull PlaybackHandle handle,
            @NonNull PlaybackState newState,
            @NonNull PlaybackState oldState) {
        for (PlaybackListener listener : listeners) {
            try {
                listener.onStateChanged(handle, newState, oldState);
            } catch (Exception e) {
                // Check if this is a test exception by class name (avoids dependency on test code)
                if (e.getClass().getName().endsWith("TestListenerException")) {
                    log.warn("Error in state change listener: {}", e.getMessage());
                } else {
                    log.warn("Error in state change listener", e);
                }
            }
        }
    }

    /**
     * Notify listeners that playback has completed. This is typically called when monitoring
     * detects the channel has stopped.
     *
     * @param handle The playback handle that completed
     */
    void notifyPlaybackComplete(@NonNull PlaybackHandle handle) {
        // First notify with FINISHED state
        notifyStateChanged(handle, PlaybackState.FINISHED, PlaybackState.PLAYING);

        // Then notify completion
        for (PlaybackListener listener : listeners) {
            try {
                listener.onPlaybackComplete(handle);
            } catch (Exception e) {
                // Check if this is a test exception by class name (avoids dependency on test code)
                if (e.getClass().getName().endsWith("TestListenerException")) {
                    log.warn("Error in completion listener: {}", e.getMessage());
                } else {
                    log.warn("Error in completion listener", e);
                }
            }
        }
    }

    /**
     * Notify listeners of current progress. Usually called internally by the progress timer, but
     * can be called manually.
     *
     * @param handle The playback handle
     * @param positionFrames Current position in frames
     * @param totalFrames Total duration in frames
     */
    void notifyProgress(@NonNull PlaybackHandle handle, long positionFrames, long totalFrames) {
        for (PlaybackListener listener : listeners) {
            try {
                listener.onProgress(handle, positionFrames, totalFrames);
            } catch (Exception e) {
                // Check if this is a test exception by class name (avoids dependency on test code)
                if (e.getClass().getName().endsWith("TestListenerException")) {
                    log.warn("Error in progress listener: {}", e.getMessage());
                } else {
                    log.warn("Error in progress listener", e);
                }
            }
        }
    }

    /**
     * Check if there are any registered listeners. Useful for optimization - no need to monitor
     * progress if nobody is listening.
     *
     * @return true if at least one listener is registered
     */
    boolean hasListeners() {
        return !listeners.isEmpty();
    }

    /**
     * Get the number of registered listeners.
     *
     * @return The count of registered listeners
     */
    int getListenerCount() {
        return listeners.size();
    }

    /**
     * Update progress for the currently monitored playback. This method is called periodically by
     * the timer and: - Queries current position from FMOD using the tracked handle - Detects if
     * playback has completed - Notifies listeners accordingly
     *
     * <p>This is package-private for testing.
     */
    void updateProgress() {
        // Capture handle in local variable to avoid race conditions
        FmodPlaybackHandle handle = currentHandle;

        // Skip if no handle or listeners
        if (handle == null || listeners.isEmpty()) {
            return;
        }

        // Check if handle is still active
        if (!handle.isActive()) {
            // Playback has stopped
            handlePlaybackStopped();
            return;
        }

        try {
            // Query current position from FMOD
            IntByReference positionRef = new IntByReference();
            int result =
                    fmod.FMOD_Channel_GetPosition(
                            handle.getChannel(), positionRef, FmodConstants.FMOD_TIMEUNIT_PCM);

            if (result == FmodConstants.FMOD_OK) {
                // Get the raw decoded position
                long decodedPosition = positionRef.getValue();

                // Apply latency compensation if system is available
                long hearingPosition = decodedPosition;
                if (system != null && handle.getAudioHandle() instanceof FmodAudioHandle) {
                    FmodAudioHandle fmodAudioHandle = (FmodAudioHandle) handle.getAudioHandle();
                    Pointer sound = fmodAudioHandle.getSound();
                    if (sound != null) {
                        hearingPosition =
                                calculateHearingPosition(
                                        decodedPosition, sound, handle.getStartFrame());
                    }
                }

                notifyProgress(handle, hearingPosition, totalFrames);

                // Check if we've reached the end (for range playback)
                if (handle.getEndFrame() != Long.MAX_VALUE
                        && hearingPosition >= handle.getEndFrame()) {
                    handlePlaybackStopped();
                }
            } else if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                // Channel has been released
                handlePlaybackStopped();
            } else {
                log.trace("Failed to get position: {}", FmodError.describe(result));
            }
        } catch (Exception e) {
            log.warn("Error updating progress", e);
        }
    }

    /**
     * Calculate the "hearing position" by compensating for audio buffer latency. This estimates
     * what the user is actually hearing through the speakers, not just what has been decoded by
     * FMOD.
     *
     * @param decodedPosition The raw position reported by FMOD (absolute)
     * @param sound The FMOD sound pointer to get source sample rate
     * @param startFrame The start frame for relative calculations
     * @return The latency-compensated hearing position (absolute)
     */
    private long calculateHearingPosition(long decodedPosition, Pointer sound, long startFrame) {
        // Calculate relative position from start frame
        long relDecoded = decodedPosition - startFrame;
        if (relDecoded < 0) relDecoded = 0;

        // Get DSP buffer configuration for latency calculation
        IntByReference bufferLengthRef = new IntByReference();
        IntByReference numBuffersRef = new IntByReference();
        int result = fmod.FMOD_System_GetDSPBufferSize(system, bufferLengthRef, numBuffersRef);
        if (result != FmodConstants.FMOD_OK) {
            // Can't calculate latency, return absolute position
            return decodedPosition;
        }

        // Get output sample rate
        IntByReference outputRateRef = new IntByReference();
        IntByReference speakerModeRef = new IntByReference();
        IntByReference rawSpeakerRef = new IntByReference();
        result =
                fmod.FMOD_System_GetSoftwareFormat(
                        system, outputRateRef, speakerModeRef, rawSpeakerRef);
        if (result != FmodConstants.FMOD_OK) {
            // Can't get output rate, return absolute position
            return decodedPosition;
        }

        // Get source sample rate from the sound
        int sourceRate = 48000; // Default if we can't get it
        try {
            FloatByReference frequency = new FloatByReference();
            IntByReference priority = new IntByReference();
            result = fmod.FMOD_Sound_GetDefaults(sound, frequency, priority);
            if (result == FmodConstants.FMOD_OK) {
                float freq = frequency.getValue();
                if (freq > 0) {
                    sourceRate = (int) freq;
                }
            }
        } catch (Exception e) {
            // Use default
        }

        int bufferLength = Math.max(0, bufferLengthRef.getValue());
        int numBuffers = Math.max(0, numBuffersRef.getValue());
        int outputRate = Math.max(0, outputRateRef.getValue());

        if (bufferLength == 0 || numBuffers == 0 || outputRate == 0) {
            return decodedPosition;
        }

        // Estimate mixer lead: buffers ahead of output plus half-buffer mix-ahead
        // This matches FmodCore's calculation exactly
        long leadFramesOutput =
                (long) bufferLength * Math.max(0, numBuffers - 1) + bufferLength / 2L;

        // Convert output lead (output frames) to source frames using rates
        long leadFramesSource = leadFramesOutput;
        if (outputRate != sourceRate) {
            // Convert with rounding to nearest
            leadFramesSource =
                    Math.round((leadFramesOutput * (double) sourceRate) / (double) outputRate);
        }

        // Clamp compensation to not exceed decoded position relative to start
        if (leadFramesSource > relDecoded) {
            leadFramesSource = relDecoded;
        }

        // Calculate audible position relative to start
        long audibleRel = relDecoded - leadFramesSource;

        // Return absolute hearing position
        return startFrame + audibleRel;
    }

    /** Handle playback stopping - notify and clean up. */
    private void handlePlaybackStopped() {
        FmodPlaybackHandle handle = currentHandle;
        if (handle != null) {
            handle.markInactive();
            notifyPlaybackComplete(handle);
        }
        stopMonitoring();
    }

    /**
     * Shutdown the listener manager and release resources. After calling this, the manager cannot
     * be used again. This will: - Stop the progress timer - Clear all listeners - Release any
     * resources
     */
    void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {

            // Stop any active monitoring
            stopMonitoring();

            // Clear all listeners
            listeners.clear();
        }
    }

    /**
     * Check if the manager has been shut down.
     *
     * @return true if shutdown() has been called
     */
    boolean isShutdown() {
        return isShutdown.get();
    }
}
