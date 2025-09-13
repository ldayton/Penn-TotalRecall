package core.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.AudioHandle;
import core.audio.AudioMetadata;
import core.audio.PlaybackHandle;
import core.dispatch.EventDispatchBus;
import core.dispatch.EventDispatcher;
import core.state.AudioSessionManager;
import core.state.AudioSessionStateMachine;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Test that SeekToStartEvent properly resets position and updates waveform. */
class SeekToStartEventTest {

    @Mock private AudioEngine audioEngine;
    @Mock private Provider<AudioEngine> audioEngineProvider;
    @Mock private AudioHandle audioHandle;
    @Mock private PlaybackHandle playbackHandle;
    @Mock private EventDispatcher eventDispatcher;

    private EventDispatchBus eventBus;
    private AudioSessionStateMachine stateMachine;
    private AudioSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        eventBus = new EventDispatchBus(eventDispatcher);
        stateMachine = new AudioSessionStateMachine();

        // Setup mock audio engine provider
        when(audioEngineProvider.get()).thenReturn(audioEngine);

        // Create AudioSessionManager with dependencies
        sessionManager = new AudioSessionManager(stateMachine, audioEngineProvider, eventBus);

        // Setup mock audio engine behavior
        when(audioEngine.loadAudio(any(String.class))).thenReturn(audioHandle);
        when(audioEngine.getMetadata(audioHandle))
                .thenReturn(
                        new AudioMetadata(44100, 2, 16, "PCM", 44100L, 1.0)); // 1 second of audio
        when(audioEngine.play(eq(audioHandle), anyLong(), anyLong())).thenReturn(playbackHandle);
    }

    @Test
    void seekToStartShouldResetPositionInReadyState() throws Exception {
        // Load test audio file - call method directly since event bus is mocked
        File testFile = new File("src/test/resources/audio/sweep.wav");
        sessionManager.onAudioFileLoadRequested(new AudioFileLoadRequestedEvent(testFile));

        // Verify in READY state after loading
        assertEquals(
                AudioSessionStateMachine.State.READY,
                stateMachine.getCurrentState(),
                "Should be in READY state after loading");

        // Start playback - call method directly
        sessionManager.onAudioPlayPauseRequested(new PlayPauseEvent());
        assertEquals(
                AudioSessionStateMachine.State.PLAYING,
                stateMachine.getCurrentState(),
                "Should be in PLAYING state");

        // Simulate position advancing
        when(audioEngine.getPosition(playbackHandle)).thenReturn(22050L); // 0.5 seconds

        // Verify position has moved forward
        assertTrue(
                sessionManager.getPlaybackPosition().orElse(0.0) > 0.4,
                "Position should have moved forward during playback");

        // Stop playback (go to start) - call method directly
        sessionManager.onAudioStopRequested(new SeekToStartEvent());

        // Should be in READY state
        assertEquals(
                AudioSessionStateMachine.State.READY,
                stateMachine.getCurrentState(),
                "Should be in READY state after stop");

        // THIS SHOULD PASS BUT WILL FAIL - position should be 0 but getPlaybackPosition returns
        // empty
        assertTrue(
                sessionManager.getPlaybackPosition().isPresent(),
                "Position should be available in READY state");
        assertEquals(
                0.0,
                sessionManager.getPlaybackPosition().orElse(-1.0),
                0.001,
                "Position should be reset to 0 after SeekToStartEvent");
    }
}
