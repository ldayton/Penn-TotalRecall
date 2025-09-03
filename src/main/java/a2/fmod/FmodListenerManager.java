package a2.fmod;

import a2.PlaybackHandle;
import a2.PlaybackListener;
import a2.PlaybackState;
import app.annotations.ThreadSafe;
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
 * Manages playback event listeners and progress monitoring for the FMOD audio engine.
 * Handles listener registration, progress callbacks, and state change notifications.
 * 
 * <p>This manager is responsible for:
 * <ul>
 *   <li>Managing PlaybackListener subscriptions</li>
 *   <li>Running periodic progress updates for active playback</li>
 *   <li>Notifying listeners of state changes</li>
 *   <li>Detecting and notifying playback completion</li>
 * </ul>
 * 
 * <p>Thread Safety: All listener notifications are executed on a dedicated timer thread
 * to avoid blocking audio operations. Listener registration is thread-safe.
 */
@ThreadSafe
@Slf4j
class FmodListenerManager {

    private static final long DEFAULT_PROGRESS_INTERVAL_MS = 100;
    
    private final long progressIntervalMs;
    private final FmodLibrary fmod;
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
     */
    FmodListenerManager(@NonNull FmodLibrary fmod) {
        this(fmod, DEFAULT_PROGRESS_INTERVAL_MS);
    }
    
    /**
     * Creates a new listener manager with custom progress interval.
     * 
     * @param fmod The FMOD library interface for position queries
     * @param progressIntervalMs Interval between progress updates in milliseconds
     */
    FmodListenerManager(@NonNull FmodLibrary fmod, long progressIntervalMs) {
        this.fmod = fmod;
        this.progressIntervalMs = progressIntervalMs;
        log.debug("FmodListenerManager initialized with progress interval: {}ms", progressIntervalMs);
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
     * Start monitoring progress for the given playback handle.
     * This will begin periodic progress callbacks to all registered listeners.
     * Only one playback can be monitored at a time - calling this stops monitoring
     * any previous playback.
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
            progressTimer = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "FmodProgressTimer");
                t.setDaemon(true);
                return t;
            });
            
            progressTimer.scheduleAtFixedRate(
                    this::updateProgress,
                    progressIntervalMs,
                    progressIntervalMs,
                    TimeUnit.MILLISECONDS);
                    
            log.debug("Started monitoring playback with {} listeners", listeners.size());
        } else {
            log.debug("No listeners registered, skipping progress timer");
        }
    }
    
    /**
     * Stop monitoring the current playback.
     * Progress callbacks will cease after this call.
     */
    void stopMonitoring() {
        currentHandle = null;
        totalFrames = 0;
        
        if (progressTimer != null) {
            progressTimer.shutdown();
            try {
                if (!progressTimer.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                    progressTimer.shutdownNow();
                }
            } catch (InterruptedException e) {
                progressTimer.shutdownNow();
                Thread.currentThread().interrupt();
            }
            progressTimer = null;
            log.debug("Stopped monitoring");
        }
    }
    
    /**
     * Notify listeners of a state change.
     * This is called immediately when state changes occur.
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
                log.warn("Error in state change listener", e);
            }
        }
    }
    
    /**
     * Notify listeners that playback has completed.
     * This is typically called when monitoring detects the channel has stopped.
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
                log.warn("Error in completion listener", e);
            }
        }
    }
    
    /**
     * Notify listeners of current progress.
     * Usually called internally by the progress timer, but can be called manually.
     * 
     * @param handle The playback handle
     * @param positionFrames Current position in frames
     * @param totalFrames Total duration in frames
     */
    void notifyProgress(
            @NonNull PlaybackHandle handle,
            long positionFrames,
            long totalFrames) {
        for (PlaybackListener listener : listeners) {
            try {
                listener.onProgress(handle, positionFrames, totalFrames);
            } catch (Exception e) {
                log.warn("Error in progress listener", e);
            }
        }
    }
    
    /**
     * Check if there are any registered listeners.
     * Useful for optimization - no need to monitor progress if nobody is listening.
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
     * Update progress for the currently monitored playback.
     * This method is called periodically by the timer and:
     * - Queries current position from FMOD using the tracked handle
     * - Detects if playback has completed
     * - Notifies listeners accordingly
     * 
     * This is package-private for testing.
     */
    void updateProgress() {
        // Skip if no handle or listeners
        if (currentHandle == null || listeners.isEmpty()) {
            return;
        }
        
        // Check if handle is still active
        if (!currentHandle.isActive()) {
            // Playback has stopped
            handlePlaybackStopped();
            return;
        }
        
        try {
            // Query current position from FMOD
            IntByReference positionRef = new IntByReference();
            int result = fmod.FMOD_Channel_GetPosition(
                    currentHandle.getChannel(),
                    positionRef,
                    FmodConstants.FMOD_TIMEUNIT_PCM);
            
            if (result == FmodConstants.FMOD_OK) {
                // Normal progress update
                long position = positionRef.getValue();
                notifyProgress(currentHandle, position, totalFrames);
                
                // Check if we've reached the end (for range playback)
                if (currentHandle.getEndFrame() != Long.MAX_VALUE 
                        && position >= currentHandle.getEndFrame()) {
                    handlePlaybackStopped();
                }
            } else if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                // Channel has been released
                handlePlaybackStopped();
            } else {
                log.trace("Failed to get position: FMOD error {}", result);
            }
        } catch (Exception e) {
            log.warn("Error updating progress", e);
        }
    }
    
    /**
     * Handle playback stopping - notify and clean up.
     */
    private void handlePlaybackStopped() {
        FmodPlaybackHandle handle = currentHandle;
        if (handle != null) {
            handle.markInactive();
            notifyPlaybackComplete(handle);
        }
        stopMonitoring();
    }
    
    /**
     * Shutdown the listener manager and release resources.
     * After calling this, the manager cannot be used again.
     * This will:
     * - Stop the progress timer
     * - Clear all listeners
     * - Release any resources
     */
    void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            log.info("Shutting down FmodListenerManager");
            
            // Stop any active monitoring
            stopMonitoring();
            
            // Clear all listeners
            listeners.clear();
            
            log.info("FmodListenerManager shutdown complete");
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