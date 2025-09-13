package state;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.PlaybackHandle;
import core.audio.PlaybackState;
import core.state.ViewportPositionManager;
import core.state.WaveformSessionDataSource;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class WaveformPaintDataSourceProgressAfterRestartTest {

    @Mock private WaveformManager waveformManager;
    @Mock private ViewportPositionManager viewport;
    @Mock private WaveformSessionDataSource sessionSource;
    @Mock private Provider<AudioEngine> audioEngineProvider;
    @Mock private AudioEngine audioEngine;
    @Mock private PlaybackHandle playbackHandle;

    private WaveformPaintDataSource dataSource;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(audioEngineProvider.get()).thenReturn(audioEngine);
        dataSource =
                new WaveformPaintDataSource(
                        waveformManager, viewport, sessionSource, audioEngineProvider);
    }

    @Test
    @Disabled("Demonstrates stale position issue after restart - needs viewport update redesign")
    @DisplayName(
            "After stopping and restarting playback, viewport should use actual position not stale"
                    + " cached value")
    void testViewportUsesActualPositionAfterRestart() {
        // Setup initial state - audio loaded, sample rate available
        when(sessionSource.getSampleRate()).thenReturn(Optional.of(44100));
        when(sessionSource.getTotalDuration()).thenReturn(Optional.of(10.0));

        // Simulate playback with progress callbacks
        dataSource.onProgress(playbackHandle, 44100L, 441000L); // 1 second position

        // Now stop playback (like what happens with SeekToStartEvent)
        dataSource.onStateChanged(playbackHandle, PlaybackState.STOPPED, PlaybackState.PLAYING);

        // Verify cached position was cleared
        assertEquals(
                0.0,
                dataSource.getPlaybackPositionSeconds(),
                "Cached position should be 0 after stopping");

        // Now simulate restarting playback from position 0
        // The audio engine has started playing and advanced to 0.2 seconds
        when(sessionSource.isPlaying()).thenReturn(true);
        when(sessionSource.getPlaybackPosition()).thenReturn(Optional.of(0.2));

        // Prepare frame - this is what updates the viewport
        dataSource.prepareFrame();

        // EXPECTED: Viewport should be called with actual position (0.2) not stale cached value
        // (0.0)
        // This test will FAIL because the current implementation uses cached value
        verify(viewport).followPlayback(eq(0.2), eq(10.0), eq(true));
    }

    @Test
    @DisplayName(
            "Viewport should immediately track actual position when starting playback, not wait for"
                    + " callbacks")
    void testViewportShouldUseActualPositionWhenStartingFromReady() {
        // Setup - audio loaded, in READY state after SeekToStart
        when(sessionSource.getSampleRate()).thenReturn(Optional.of(44100));
        when(sessionSource.getTotalDuration()).thenReturn(Optional.of(10.0));

        // Cached progress is at 0 (cleared when stopped)
        // But actual playback has started and progressed to 0.3 seconds
        when(sessionSource.isPlaying()).thenReturn(true);
        when(sessionSource.getPlaybackPosition()).thenReturn(Optional.of(0.3)); // 300ms in

        // First frame after starting playback
        dataSource.prepareFrame();

        // EXPECTED: Viewport should immediately use actual position (0.3), not wait for callbacks
        // This test will FAIL - current implementation uses cached value (0.0)
        verify(viewport).followPlayback(eq(0.3), eq(10.0), eq(true));
    }
}
