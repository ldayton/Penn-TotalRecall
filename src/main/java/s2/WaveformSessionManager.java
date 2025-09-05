package s2;

import a2.AudioEngine;
import a2.AudioHandle;
import com.google.inject.Provider;
import events.AppStateChangedEvent;
import events.EventDispatchBus;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import w2.TimeRange;
import w2.Waveform;
import w2.WaveformPainter;
import w2.WaveformPaintingDataSource;
import w2.WaveformViewport;

/**
 * Manages thei waveform session, coordinating rendering based on audio state. Replaces the
 * monolithic WaveformDisplay with clean separation of concerns.
 */
@Singleton
@Slf4j
public class WaveformSessionManager implements WaveformPaintingDataSource {

    private static final int PIXELS_PER_SECOND = 200;

    private final WaveformSessionSource sessionSource;
    private final WaveformPainter painter;
    private final Provider<AudioEngine> audioEngineProvider;

    private Optional<Waveform> currentWaveform = Optional.empty();
    private Optional<WaveformViewport> viewport = Optional.empty();
    private Optional<AudioEngine> audioEngine = Optional.empty();
    private double viewStartSeconds = 0.0; // View start position

    @Inject
    public WaveformSessionManager(
            @NonNull WaveformSessionSource sessionSource,
            @NonNull WaveformPainter painter,
            @NonNull Provider<AudioEngine> audioEngineProvider,
            @NonNull EventDispatchBus eventBus,
            @NonNull ui.WaveformCanvas canvas) {
        this.sessionSource = sessionSource;
        this.painter = painter;
        this.audioEngineProvider = audioEngineProvider;
        painter.setDataSource(this); // We are the data source
        eventBus.subscribe(this);

        // Set the canvas as our viewport
        setViewport(canvas);
    }

    /** Set the viewport that this coordinator manages. */
    public void setViewport(@NonNull WaveformViewport viewport) {
        this.viewport = Optional.of(viewport);
        painter.setViewport(viewport);
        painter.setDataSource(this); // We implement WaveformPaintingDataSource

        // If we're playing, start the repaint timer
        if (sessionSource.isPlaying()) {
            painter.start();
        }
    }

    /** Set the waveform to render. */
    public void setWaveform(@NonNull Waveform waveform) {
        this.currentWaveform = Optional.of(waveform);
        requestRepaint();
    }

    /** Clear the current waveform. */
    public void clearWaveform() {
        this.currentWaveform = Optional.empty();
        requestRepaint();
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        log.debug("Handling state change: {} -> {}", event.getPreviousState(), event.getNewState());

        switch (event.getNewState()) {
            case PLAYING -> {
                // Start repaint timer for smooth playback cursor
                painter.start();
                log.debug("Started waveform repaint timer");
            }

            case PAUSED -> {
                // Stop repaint timer but keep display
                painter.stop();
                requestRepaint(); // One final paint to show stopped state
                log.debug("Stopped waveform repaint timer");
            }

            case READY -> {
                // Stop repaint timer
                painter.stop();

                // If transitioning from LOADING to READY, we need a waveform
                if (event.getPreviousState() == AudioSessionStateMachine.State.LOADING) {
                    createWaveformForCurrentAudio();
                }

                requestRepaint();
                log.debug("Ready state");
            }

            case NO_AUDIO -> {
                // Clear waveform and stop timer
                painter.stop();
                // Clean up waveform resources
                currentWaveform.ifPresent(Waveform::shutdown);
                clearWaveform();
                log.debug("Cleared waveform display");
            }

            case LOADING -> {
                // Show loading state
                requestRepaint();
                log.debug("Showing loading state");
            }

            case ERROR -> {
                // Show error state
                painter.stop();
                requestRepaint();
                log.debug("Showing error state");
            }
        }
    }

    /** Request a repaint of the waveform display. */
    public void requestRepaint() {
        if (viewport.isPresent()) {
            painter.requestRepaint();
        }
    }

    /** Get the current waveform being rendered. */
    public Optional<Waveform> getCurrentWaveform() {
        return currentWaveform;
    }

    /** Check if actively rendering (timer running). */
    public boolean isActivelyRendering() {
        return painter.isRunning();
    }

    /** Force stop all rendering activity. */
    public void stopRendering() {
        painter.stop();
    }

    // WaveformPaintingDataSource implementation

    @Override
    public TimeRange getTimeRange() {
        if (!viewport.isPresent()) {
            return null;
        }

        // Calculate visible time range based on viewport width and zoom
        double widthSeconds = viewport.get().getBounds().width / (double) PIXELS_PER_SECOND;
        double endSeconds = viewStartSeconds + widthSeconds;

        // Clamp to audio duration if we have it
        Optional<Double> totalDuration = sessionSource.getTotalDuration();
        if (totalDuration.isPresent() && totalDuration.get() > 0) {
            endSeconds = Math.min(endSeconds, totalDuration.get());
        }

        return new TimeRange(viewStartSeconds, endSeconds);
    }

    @Override
    public int getPixelsPerSecond() {
        return PIXELS_PER_SECOND;
    }

    @Override
    public double getPlaybackPositionSeconds() {
        return sessionSource.getPlaybackPosition().orElse(0.0);
    }

    @Override
    public Waveform getWaveform() {
        return currentWaveform.orElse(null);
    }

    @Override
    public boolean isPlaying() {
        return sessionSource.isPlaying();
    }

    private void createWaveformForCurrentAudio() {
        // Get audio handle and path from session source
        Optional<AudioHandle> audioHandle = sessionSource.getCurrentAudioHandle();
        Optional<String> audioPath = sessionSource.getCurrentAudioFilePath();

        if (audioHandle.isPresent() && audioPath.isPresent()) {
            // Get or create audio engine
            if (audioEngine.isEmpty()) {
                audioEngine = Optional.of(audioEngineProvider.get());
            }

            // Create new waveform for the audio file
            Waveform waveform = new Waveform(audioPath.get(), audioEngine.get(), audioHandle.get());

            // Clean up old waveform if present
            currentWaveform.ifPresent(Waveform::shutdown);

            // Set the new waveform
            currentWaveform = Optional.of(waveform);
            log.info("Created waveform for: {}", audioPath.get());
        } else {
            log.warn("Cannot create waveform - missing audio handle or path");
        }
    }
}
