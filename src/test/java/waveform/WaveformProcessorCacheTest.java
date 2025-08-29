package waveform;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import audio.FmodCore;
import audio.FmodCore.ChunkData;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WaveformProcessorCacheTest {

    @Test
    void shouldCacheBandPassFiltersForSameFrequencyRange() throws Exception {
        FmodCore mockFmodCore = Mockito.mock(FmodCore.class);
        PixelScaler mockPixelScaler = Mockito.mock(PixelScaler.class);

        ChunkData chunkData = new ChunkData(new double[] {0.1, 0.2, -0.1, 0.3}, 44100, 1, 0, 4);

        when(mockFmodCore.readAudioChunk(anyString(), anyInt(), anyDouble(), anyDouble()))
                .thenReturn(chunkData);
        when(mockPixelScaler.toPixelResolution(
                        Mockito.any(double[].class), anyInt(), anyInt(), anyInt()))
                .thenReturn(new double[] {0.1, 0.2, 0.1, 0.3});
        when(mockPixelScaler.smoothPixels(Mockito.any(double[].class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WaveformProcessor processor = new WaveformProcessor(mockFmodCore, mockPixelScaler, true);

        double[] firstResult =
                processor.processAudioForDisplay("test.wav", 0, 10.0, 0.25, 0.1, 0.4, 100);

        double[] secondResult =
                processor.processAudioForDisplay("test.wav", 1, 10.0, 0.25, 0.1, 0.4, 100);

        assertArrayEquals(firstResult, secondResult);
    }
}
