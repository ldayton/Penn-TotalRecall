package core.audio.fmod;

import com.google.errorprone.annotations.ThreadSafe;
import core.audio.PlaybackHandle;
import core.audio.PlaybackListener;
import core.audio.PlaybackState;
import core.audio.fmod.panama.FmodCore;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
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

    private static final long DEFAULT_PROGRESS_INTERVAL_MS = 15;

    private final long progressIntervalMs;
    private final MemorySegment system; // FMOD system pointer
    private ScheduledExecutorService progressTimer;

    // Current monitoring state
    private volatile FmodPlaybackHandle currentHandle;
    // Total duration in frames for the monitored segment
    private volatile long totalFrames;
    // Start frame offset for the current segment
    private volatile long startFrame;
    // Current position in frames
    private volatile long currentPositionFrames;

    // Listener management
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    /**
     * Creates a new listener manager with default progress interval.
     *
     * @param system The FMOD system pointer
     */
    FmodListenerManager(@NonNull MemorySegment system) {
        this(system, DEFAULT_PROGRESS_INTERVAL_MS);
    }

    /**
     * Creates a new listener manager with custom progress interval.
     *
     * @param system The FMOD system pointer
     * @param progressIntervalMs Interval between progress updates in milliseconds
     */
    FmodListenerManager(@NonNull MemorySegment system, long progressIntervalMs) {
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
        this.startFrame = handle.getStartFrame();
        this.currentPositionFrames = startFrame;

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
        totalFrames = 0L;
        currentPositionFrames = 0L;

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

        // Check if channel is still playing (FMOD may have stopped it via SetDelay)
        try (Arena arena = Arena.ofConfined()) {
            var isPlayingRef = arena.allocate(ValueLayout.JAVA_INT);
            int result = FmodCore.FMOD_Channel_IsPlaying(handle.getChannel(), isPlayingRef);

            if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                // Channel was released
                handlePlaybackStopped();
                return;
            }

            if (result == FmodConstants.FMOD_OK && isPlayingRef.get(ValueLayout.JAVA_INT, 0) == 0) {
                // Channel stopped playing (e.g., reached dspclock_end from SetDelay)
                handlePlaybackStopped();
                return;
            }
        } catch (Exception e) {
            log.warn("Error checking channel playing status", e);
        }

        // Query current position from FMOD
        try (Arena arena = Arena.ofConfined()) {
            var positionRef = arena.allocate(ValueLayout.JAVA_INT);
            int result =
                    FmodCore.FMOD_Channel_GetPosition(
                            handle.getChannel(), positionRef, FmodConstants.FMOD_TIMEUNIT_PCM);

            if (result == FmodConstants.FMOD_OK) {
                // Get frame-accurate position
                long absoluteFrames = positionRef.get(ValueLayout.JAVA_INT, 0);
                currentPositionFrames = absoluteFrames;

                // Check if we've reached or passed the end frame
                if (handle.getEndFrame() != Long.MAX_VALUE) {
                    if (absoluteFrames >= handle.getEndFrame()) {
                        // Playback reached the specified end frame
                        handlePlaybackStopped();
                        return;
                    }
                }

                // Notify progress with frame values
                notifyProgress(handle, currentPositionFrames, totalFrames);
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

    /** Get current position in frames. */
    long getCurrentPositionFrames() {
        // Return cached position from update thread
        return currentPositionFrames;
    }

    /** Update position after a seek to a new absolute frame position. */
    void onSeek(@NonNull FmodPlaybackHandle handle, long newFrame) {
        if (currentHandle != handle) {
            return;
        }
        long clamped = Math.max(handle.getStartFrame(), Math.min(newFrame, handle.getEndFrame()));
        this.currentPositionFrames = clamped;
    }
}
