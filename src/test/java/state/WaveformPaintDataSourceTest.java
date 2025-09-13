package state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.PlaybackHandle;
import core.state.WaveformSessionDataSource;
import core.state.WaveformViewport;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Verifies that when not playing (e.g., after SeekToStart/stop), the paint data source uses the
 * session-reported position (0.0) rather than a stale cached progress value.
 */
class WaveformPaintDataSourceTest {

    @Mock private WaveformManager waveformManager;
    @Mock private WaveformViewport viewport;
    @Mock private WaveformSessionDataSource sessionSource;
    @Mock private Provider<AudioEngine> audioEngineProvider;
    @Mock private AudioEngine audioEngine;
    @Mock private PlaybackHandle playbackHandle;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(audioEngineProvider.get()).thenReturn(audioEngine);
        // Common session metadata
        when(sessionSource.getSampleRate()).thenReturn(Optional.of(44100));
        when(sessionSource.getTotalDuration()).thenReturn(Optional.of(1.0)); // 1 second
    }

    @Test
    void prepareFrame_usesSessionPositionWhenNotPlaying_afterStop() {
        // Given a paint data source and some prior progress (e.g., 0.5s)
        var paintDataSource =
                new WaveformPaintDataSource(
                        waveformManager, viewport, sessionSource, audioEngineProvider);

        paintDataSource.onProgress(playbackHandle, 22050, 44100); // cache ~0.5s

        // When not playing (READY/PAUSED), session reports position 0.0
        when(sessionSource.isPlaying()).thenReturn(false);
        when(sessionSource.getPlaybackPosition()).thenReturn(Optional.of(0.0));

        // When
        paintDataSource.prepareFrame();

        // Then viewport should be updated using 0.0, not the stale 0.5
        ArgumentCaptor<Double> positionCaptor = ArgumentCaptor.forClass(Double.class);
        verify(viewport).followPlayback(positionCaptor.capture(), eq(1.0), eq(false));
        assertEquals(0.0, positionCaptor.getValue(), 1e-6);
    }
}
