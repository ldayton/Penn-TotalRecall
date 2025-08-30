package w2;

import static org.junit.jupiter.api.Assertions.*;

import audio.AudioSystemManager;
import audio.FmodCore;
import com.github.romankh3.image.comparison.ImageComparison;
import com.github.romankh3.image.comparison.model.ImageComparisonResult;
import com.github.romankh3.image.comparison.model.ImageComparisonState;
import env.AppConfig;
import env.Platform;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrates pixel-perfect equivalence between w2 and original waveform rendering.
 *
 * <p>This test proves that the new w2 viewport-aware async implementation produces exactly the same
 * visual output as the original waveform package, validating that:
 *
 * <ul>
 *   <li>Audio processing pipeline is identical (WaveformProcessor reuse)
 *   <li>Rendering colors and styles match exactly
 *   <li>Segment-based caching doesn't introduce artifacts
 *   <li>Async CompletableFuture approach maintains correctness
 * </ul>
 */
@audio.AudioEngine
class W2WaveformImageRegressionTest {
    private static final Logger logger =
            LoggerFactory.getLogger(W2WaveformImageRegressionTest.class);

    // Test configuration - same as original test for direct comparison
    private static final String SAMPLE_FILE_PATH = "packaging/samples/sample.wav";
    private static final String OUTPUT_DIR = "build/test-results/w2-waveform-images";
    private static final String REFERENCE_IMAGE_RESOURCE_PATH = "/waveform-reference-images/";
    private static final int TEST_CHUNK_COUNT = 3;
    private static final int TEST_IMAGE_HEIGHT = 800;
    private static final int TEST_IMAGE_WIDTH = 200 * 10; // 10 seconds at 200px/sec

    private FmodCore fmodCore;
    private waveform.Waveform originalWaveform;
    private w2.Waveform w2Waveform;

    @BeforeEach
    void setUp() {
        // Initialize FmodCore with required dependencies
        Platform platform = new Platform();
        AppConfig appConfig = new AppConfig();
        AudioSystemManager audioManager = new AudioSystemManager(appConfig, platform);
        fmodCore = new FmodCore(audioManager);

        // Create output directory for test images
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory: " + OUTPUT_DIR, e);
        }

        // Create original waveform renderer
        originalWaveform =
                waveform.Waveform.builder(fmodCore)
                        .audioFile(SAMPLE_FILE_PATH)
                        .timeResolution(200) // 200 pixels per second
                        .amplitudeResolution(TEST_IMAGE_HEIGHT)
                        .build();

        // Create w2 waveform renderer
        w2Waveform = w2.Waveform.forAudioFile(SAMPLE_FILE_PATH, fmodCore);
    }

    @AfterEach
    void tearDown() {
        if (w2Waveform instanceof WaveformImpl) {
            ((WaveformImpl) w2Waveform).shutdown();
        }
        fmodCore = null;
    }

    @Test
    void testW2ProducesIdenticalWaveformImages() throws Exception {
        assertTimeoutPreemptively(
                Duration.ofSeconds(10),
                () -> {
                    logger.info("Testing w2 produces pixel-perfect identical waveforms...");

                    for (int chunkNum = 0; chunkNum < TEST_CHUNK_COUNT; chunkNum++) {
                        // Render with original implementation
                        Image originalImage = originalWaveform.renderChunk(chunkNum).image();
                        BufferedImage originalBuffered = toBufferedImage(originalImage);

                        // Render with w2 implementation
                        // Calculate viewport for this chunk (10 seconds per chunk at 200px/sec)
                        double startTime = chunkNum * 10.0;
                        double endTime = (chunkNum + 1) * 10.0;
                        ViewportContext viewport =
                                new ViewportContext(
                                        startTime,
                                        endTime,
                                        TEST_IMAGE_WIDTH,
                                        TEST_IMAGE_HEIGHT,
                                        200, // pixels per second
                                        ViewportContext.ScrollDirection.FORWARD);

                        CompletableFuture<Image> w2Future = w2Waveform.renderViewport(viewport);
                        Image w2Image = w2Future.get(30, TimeUnit.SECONDS);
                        BufferedImage w2Buffered = toBufferedImage(w2Image);

                        // Compare images pixel-perfect
                        compareImages(originalBuffered, w2Buffered, chunkNum);

                        logger.info("✓ Chunk {} - w2 matches original exactly", chunkNum);
                    }

                    logger.info(
                            "SUCCESS: w2 implementation produces pixel-perfect identical"
                                    + " waveforms!");
                });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testW2MatchesReferenceImages() throws Exception {
        logger.info("Testing w2 against reference images...");

        for (int chunkNum = 0; chunkNum < TEST_CHUNK_COUNT; chunkNum++) {
            // Load reference image
            String filename = String.format("fullrange_chunk%d.png", chunkNum);
            BufferedImage referenceImage = loadReferenceImage(filename);

            // Render with w2
            double startTime = chunkNum * 10.0;
            double endTime = (chunkNum + 1) * 10.0;
            ViewportContext viewport =
                    new ViewportContext(
                            startTime,
                            endTime,
                            TEST_IMAGE_WIDTH,
                            TEST_IMAGE_HEIGHT,
                            200,
                            ViewportContext.ScrollDirection.FORWARD);

            CompletableFuture<Image> w2Future = w2Waveform.renderViewport(viewport);
            Image w2Image = w2Future.get(30, TimeUnit.SECONDS);
            BufferedImage w2Buffered = toBufferedImage(w2Image);

            // Compare against reference
            ImageComparison comparison =
                    new ImageComparison(referenceImage, w2Buffered)
                            .setThreshold(0) // Pixel-perfect
                            .setRectangleLineWidth(1)
                            .setMinimalRectangleSize(1);
            ImageComparisonResult result = comparison.compareImages();

            if (result.getImageComparisonState() != ImageComparisonState.MATCH) {
                Path debugFile = Paths.get(OUTPUT_DIR, "w2_" + filename);
                Path diffFile = Paths.get(OUTPUT_DIR, "w2_diff_" + filename);

                ImageIO.write(w2Buffered, "png", debugFile.toFile());
                ImageIO.write(result.getResult(), "png", diffFile.toFile());

                fail(
                        String.format(
                                "w2 doesn't match reference for %s. Debug: %s, Diff: %s",
                                filename, debugFile, diffFile));
            }

            logger.info("✓ {} - w2 matches reference", filename);
        }
    }

    /** Compares two images and asserts they are pixel-perfect identical. */
    private void compareImages(BufferedImage expected, BufferedImage actual, int chunkNum)
            throws IOException {
        // First check dimensions
        assertEquals(
                expected.getWidth(), actual.getWidth(), "Width mismatch for chunk " + chunkNum);
        assertEquals(
                expected.getHeight(), actual.getHeight(), "Height mismatch for chunk " + chunkNum);

        // Use image comparison library for detailed analysis
        ImageComparison comparison =
                new ImageComparison(expected, actual)
                        .setThreshold(0) // 0 tolerance - must be pixel-perfect
                        .setRectangleLineWidth(1)
                        .setMinimalRectangleSize(1);

        ImageComparisonResult result = comparison.compareImages();

        if (result.getImageComparisonState() != ImageComparisonState.MATCH) {
            // Save debug images
            String expectedFile = String.format("original_chunk%d.png", chunkNum);
            String actualFile = String.format("w2_chunk%d.png", chunkNum);
            String diffFile = String.format("diff_chunk%d.png", chunkNum);

            Path expectedPath = Paths.get(OUTPUT_DIR, expectedFile);
            Path actualPath = Paths.get(OUTPUT_DIR, actualFile);
            Path diffPath = Paths.get(OUTPUT_DIR, diffFile);

            ImageIO.write(expected, "png", expectedPath.toFile());
            ImageIO.write(actual, "png", actualPath.toFile());
            ImageIO.write(result.getResult(), "png", diffPath.toFile());

            fail(
                    String.format(
                            "Chunk %d: w2 doesn't match original. Original: %s, w2: %s, Diff: %s",
                            chunkNum, expectedPath, actualPath, diffPath));
        }
    }

    /** Loads a reference image from test resources. */
    private BufferedImage loadReferenceImage(String filename) throws IOException {
        String resourcePath = REFERENCE_IMAGE_RESOURCE_PATH + filename;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Reference image not found: " + resourcePath);
            }
            return ImageIO.read(is);
        }
    }

    /** Converts an Image to BufferedImage for comparison. */
    private BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }

        BufferedImage bufferedImage =
                new BufferedImage(
                        image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        bufferedImage.createGraphics().drawImage(image, 0, 0, null);
        return bufferedImage;
    }
}
