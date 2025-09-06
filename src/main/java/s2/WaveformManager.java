package s2;

import a2.AudioEngine;
import a2.AudioHandle;
import a2.SampleReaderFactory;
import com.google.inject.Provider;
import events.AppStateChangedEvent;
import events.EventDispatchBus;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import w2.Waveform;

/**
 * Manages waveform instance lifecycle. Creates and destroys waveform instances when audio is loaded
 * or closed.
 */
@Singleton
@Slf4j
public class WaveformManager {

    private final WaveformSessionDataSource sessionSource;
    private final Provider<AudioEngine> audioEngineProvider;
    private final Provider<SampleReaderFactory> sampleReaderFactoryProvider;

    private Optional<Waveform> currentWaveform = Optional.empty();
    private Optional<AudioEngine> audioEngine = Optional.empty();
    private Optional<SampleReaderFactory> sampleReaderFactory = Optional.empty();

    @Inject
    public WaveformManager(
            @NonNull WaveformSessionDataSource sessionSource,
            @NonNull Provider<AudioEngine> audioEngineProvider,
            @NonNull Provider<SampleReaderFactory> sampleReaderFactoryProvider,
            @NonNull EventDispatchBus eventBus) {
        this.sessionSource = sessionSource;
        this.audioEngineProvider = audioEngineProvider;
        this.sampleReaderFactoryProvider = sampleReaderFactoryProvider;
        eventBus.subscribe(this);
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        log.debug("Handling state change: {} -> {}", event.getPreviousState(), event.getNewState());

        switch (event.getNewState()) {
            case READY -> {
                // If transitioning from LOADING to READY, we need a waveform
                if (event.getPreviousState() == AudioSessionStateMachine.State.LOADING) {
                    createWaveformForCurrentAudio();
                }
            }

            case NO_AUDIO -> {
                // Clean up waveform resources
                currentWaveform.ifPresent(Waveform::shutdown);
                clearWaveform();
                log.debug("Cleared waveform display");
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

            // Get or create sample reader factory
            if (sampleReaderFactory.isEmpty()) {
                sampleReaderFactory = Optional.of(sampleReaderFactoryProvider.get());
            }

            // Create new waveform for the audio file
            Waveform waveform =
                    new Waveform(
                            audioPath.get(),
                            audioEngine.get(),
                            audioHandle.get(),
                            sampleReaderFactory.get());

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
