package core.viewport;

import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.SeekEvent;
import core.events.ZoomEvent;
import lombok.NonNull;

/**
 * Viewport session for a specific audio file. Created when audio loads with the file's sample rate.
 * Disposed when audio closes. Manages playback listener for viewport synchronization.
 */
public class ViewportSession {

    private final ViewportSessionManager viewportManager;
    private final EventDispatchBus eventBus;
    private final int sampleRate;
    private final ViewportPlaybackListener playbackListener;

    public ViewportSession(
            @NonNull EventDispatchBus eventBus,
            @NonNull ViewportSessionManager viewportManager,
            @NonNull Provider<AudioEngine> audioEngineProvider,
            int sampleRate) {
        this.eventBus = eventBus;
        this.viewportManager = viewportManager;
        this.sampleRate = sampleRate;

        // Create and register playback listener
        this.playbackListener = new ViewportPlaybackListener(viewportManager, sampleRate);
        AudioEngine audioEngine = audioEngineProvider.get();
        audioEngine.addPlaybackListener(playbackListener);

        eventBus.subscribe(this);
    }

    @Subscribe
    public void onZoom(@NonNull ZoomEvent event) {
        ViewportContext current = viewportManager.getContext();
        double factor = event.direction() == ZoomEvent.Direction.IN ? 1.5 : (1.0 / 1.5);
        int newPixelsPerSecond = (int) (current.pixelsPerSecond() * factor);
        viewportManager.onUserZoom(newPixelsPerSecond);
    }

    @Subscribe
    public void onSeek(@NonNull SeekEvent event) {
        double targetSeconds = event.frame() / (double) sampleRate;
        viewportManager.onUserSeek(targetSeconds);
    }

    /**
     * Update viewport for playback progress. Called during paint preparation when audio is playing.
     */
    public void updatePlaybackPosition(double positionSeconds) {
        viewportManager.onPlaybackUpdate(positionSeconds);
    }

    /** Update viewport canvas dimensions. Called when the component is resized. */
    public void updateCanvasSize(int width, int height) {
        viewportManager.onCanvasResize(width, height);
    }

    /** Get the current viewport context for rendering. */
    public ViewportContext getViewportContext() {
        return viewportManager.getContext();
    }

    /**
     * Clean up resources when audio session ends. Unsubscribes from event bus and removes playback
     * listener.
     */
    public void dispose(@NonNull Provider<AudioEngine> audioEngineProvider) {
        eventBus.unsubscribe(this);

        // Unregister playback listener
        AudioEngine audioEngine = audioEngineProvider.get();
        audioEngine.removePlaybackListener(playbackListener);
    }

    /** Get current playback position in seconds. */
    public double getPlaybackPositionSeconds() {
        return playbackListener.getPlaybackPositionSeconds();
    }

    /** Check if currently playing. */
    public boolean isPlaying() {
        return playbackListener.isPlaying();
    }
}
