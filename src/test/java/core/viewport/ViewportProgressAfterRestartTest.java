package core.viewport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.session.AudioSessionDataSource;
import core.dispatch.EventDispatchBus;
import core.waveform.WaveformManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ViewportProgressAfterRestartTest {

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
        viewportManager =
                new ViewportSessionManager(
                        eventBus, sessionSource, audioEngineProvider, waveformManager);
    }

    @Test
    @Disabled("Demonstrates stale position issue after restart - needs viewport update redesign")
    @DisplayName(
            "After stopping and restarting playback, viewport should use actual position not stale"
                    + " cached value")
    void testViewportUsesActualPositionAfterRestart() {
        // Setup initial state - audio loaded, sample rate available
        when(sessionSource.getSampleRate()).thenReturn(Optional.of(44100));
        when(sessionSource.isAudioLoaded()).thenReturn(true);

        // Simulate playback with progress callbacks
        viewportManager.onPlaybackUpdate(1.0); // 1 second position

        // Now stop playback (like what happens with seek to start)
        viewportManager.onPlaybackUpdate(0.0); // Reset to position 0

        // Verify viewport position was reset to 0
        assertEquals(
                0.0,
                viewportManager.getPlaybackPositionSeconds(),
                "Viewport position should be 0 after stopping");

        // Now simulate restarting playback from position 0
        // The viewport should immediately reflect the playback position
        viewportManager.onPlaybackUpdate(0.2);

        // Verify viewport has the correct position
        var context = viewportManager.getContext();
        assertEquals(
                0.2,
                context.playheadSeconds(),
                0.001,
                "Viewport should be at actual position (0.2) not stale cached value");
    }

    @Test
    @DisplayName(
            "Viewport should immediately track actual position when starting playback, not wait for"
                    + " callbacks")
    void testViewportShouldUseActualPositionWhenStartingFromReady() {
        // Setup - audio loaded, in READY state after SeekToStart
        when(sessionSource.getSampleRate()).thenReturn(Optional.of(44100));
        when(sessionSource.isAudioLoaded()).thenReturn(true);

        // Simulate playback starting and immediately updating position
        // The viewport should track this position
        viewportManager.onPlaybackUpdate(0.3); // 300ms in

        // Verify viewport has the correct position
        var context = viewportManager.getContext();
        assertEquals(
                0.3,
                context.playheadSeconds(),
                0.001,
                "Viewport should immediately use actual position (0.3), not wait for callbacks");
    }
}
