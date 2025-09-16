package core.waveform.signal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import core.audio.AudioData;
import core.audio.SampleReader;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class WaveformProcessorTest {

    private static final int SAMPLE_RATE = 44100;
    private static final String TEST_AUDIO_PATH = "/test/audio.wav";
    private static final double CHUNK_DURATION = 10.0;
    private static final int TARGET_PIXEL_WIDTH = 200;

    @Mock private SampleReader sampleReader;
    @Mock private PixelScaler pixelScaler;

    private WaveformProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new WaveformProcessor(sampleReader, SAMPLE_RATE, pixelScaler);
    }

    @Test
    @DisplayName("First chunk (index 0) should NOT skip any samples")
    void testFirstChunkDoesNotSkipSamples() throws Exception {
        // Given: Processing the first chunk (index 0)
        int chunkIndex = 0;

        // Create mock audio data
        int frameCount = (int) (CHUNK_DURATION * SAMPLE_RATE);
        double[] testSamples = new double[frameCount];
        AudioData mockAudioData = new AudioData(testSamples, SAMPLE_RATE, 1, 0L, (long) frameCount);

        // Mock the sample reader to return our test data
        when(sampleReader.readSamples(any(Path.class), anyLong(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(mockAudioData));

        // Mock the pixel scaler to capture what it receives
        double[] scaledResult = new double[TARGET_PIXEL_WIDTH];
        when(pixelScaler.toPixelResolution(any(double[].class), anyInt(), anyInt(), anyInt()))
                .thenReturn(scaledResult);
        when(pixelScaler.smoothPixels(any(double[].class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Processing audio for display
        processor.processAudioForDisplay(
                TEST_AUDIO_PATH,
                chunkIndex,
                TARGET_PIXEL_WIDTH);

        // Then: Verify that NO samples are skipped for the first chunk
        ArgumentCaptor<Integer> skipSamplesCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(pixelScaler)
                .toPixelResolution(
                        any(double[].class),
                        skipSamplesCaptor.capture(),
                        eq(TARGET_PIXEL_WIDTH),
                        anyInt());

        int actualSkippedSamples = skipSamplesCaptor.getValue();
        assertEquals(
                0,
                actualSkippedSamples,
                "First chunk should NOT skip any samples, but it skips "
                        + actualSkippedSamples
                        + " samples (approximately "
                        + (actualSkippedSamples / (double) SAMPLE_RATE)
                        + " seconds)");
    }
}
