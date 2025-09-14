package core.waveform;

import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.AudioHandle;
import core.audio.SampleReader;
import core.audio.session.AudioSessionDataSource;
import core.audio.session.AudioSessionStateMachine;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages waveform instance lifecycle. Creates and destroys waveform instances when audio is loaded
 * or closed.
 */
@Singleton
@Slf4j
public class WaveformManager {

    private final AudioSessionDataSource sessionSource;
    private final Provider<AudioEngine> audioEngineProvider;
    private final Provider<SampleReader> sampleReaderProvider;

    private Optional<Waveform> currentWaveform = Optional.empty();
    private Optional<AudioEngine> audioEngine = Optional.empty();

    @Inject
    public WaveformManager(
            @NonNull AudioSessionDataSource sessionSource,
            @NonNull Provider<AudioEngine> audioEngineProvider,
            @NonNull Provider<SampleReader> sampleReaderProvider,
            @NonNull EventDispatchBus eventBus) {
        this.sessionSource = sessionSource;
        this.audioEngineProvider = audioEngineProvider;
        this.sampleReaderProvider = sampleReaderProvider;
        eventBus.subscribe(this);
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        log.debug("Handling state change: {} -> {}", event.previousState(), event.newState());

        switch (event.newState()) {
            case READY -> {
                // If transitioning from LOADING to READY, we need a waveform
                if (event.previousState() == AudioSessionStateMachine.State.LOADING) {
                    createWaveformForCurrentAudio();
                }
            }

            case NO_AUDIO, ERROR -> {
                // Clean up waveform resources
                currentWaveform.ifPresent(Waveform::shutdown);
                clearWaveform();
                log.debug("Cleared waveform display");
            }

            case LOADING, PLAYING, PAUSED -> {
                // No waveform changes needed for these states
            }
        }
    }

    /** Get the current waveform being rendered. */
    public Optional<Waveform> getCurrentWaveform() {
        return currentWaveform;
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

            // Create new waveform for the audio file with a new sample reader
            Waveform waveform =
                    new Waveform(
                            audioPath.get(),
                            audioEngine.get(),
                            audioHandle.get(),
                            sampleReaderProvider.get());

            // Clean up old waveform if present
            currentWaveform.ifPresent(Waveform::shutdown);

            // Set the new waveform
            currentWaveform = Optional.of(waveform);
            log.info("Created waveform for: {}", audioPath.get());
        } else {
            log.warn("Cannot create waveform - missing audio handle or path");
        }
    }

    private void clearWaveform() {
        currentWaveform = Optional.empty();
    }
}
