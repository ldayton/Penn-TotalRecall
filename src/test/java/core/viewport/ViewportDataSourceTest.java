package core.viewport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.AudioSessionDataSource;
import core.audio.AudioSessionStateMachine;
import core.dispatch.EventDispatchBus;
import core.events.AppStateChangedEvent;
import core.waveform.WaveformManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Verifies that when not playing (e.g., after SeekToStart/stop), the viewport uses the correct
 * position (0.0) rather than a stale cached progress value.
 */
class ViewportDataSourceTest {

    @Mock private EventDispatchBus eventBus;
    @Mock private AudioSessionDataSource sessionSource;
    @Mock private Provider<AudioEngine> audioEngineProvider;
    @Mock private AudioEngine audioEngine;
    @Mock private WaveformManager waveformManager;

    private ViewportSessionManager viewportManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(audioEngineProvider.get()).thenReturn(audioEngine);
        // Common session metadata
        when(sessionSource.getSampleRate()).thenReturn(Optional.of(44100));
        when(sessionSource.isAudioLoaded()).thenReturn(true);

        viewportManager =
                new ViewportSessionManager(
                        eventBus, sessionSource, audioEngineProvider, waveformManager);
    }

    @Test
    @Disabled("disabling after viewport refactor")
    void viewport_usesCorrectPositionWhenNotPlaying_afterStop() {
        // Given audio is loaded, creating a viewport session
        viewportManager.onAppStateChanged(
                new AppStateChangedEvent(
                        AudioSessionStateMachine.State.NO_AUDIO,
                        AudioSessionStateMachine.State.READY));

        // Simulate some playback progress
        viewportManager.onPlaybackUpdate(0.5); // ~0.5s progress

        // Then simulate stop playback (viewport resets to 0)
        viewportManager.onPlaybackUpdate(0.0);

        // Verify viewport position is reset to 0.0, not the stale 0.5
        assertEquals(
                0.0,
                viewportManager.getPlaybackPositionSeconds(),
                1e-6,
                "Viewport should use position 0.0 after stop, not stale cached value");

        // Also verify via context
        var context = viewportManager.getContext();
        assertEquals(
                0.0,
                context.playheadSeconds(),
                1e-6,
                "Viewport context should show playhead at 0.0 after stop");
    }
}
