package waveform;

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
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visual regression tests for waveform rendering.
 *
 * <p>Generates waveform images using different frequency ranges and compares them pixel-perfect
 * against reference images to detect visual regressions in the waveform rendering pipeline.
 *
 * <p><strong>Test Coverage:</strong>
 *
 * <ul>
 *   <li>Full-range (0.001-0.45 Hz) - Tests complete frequency spectrum
 *   <li>Low-pass (0.001-0.1 Hz) - Tests low-frequency filtering
 *   <li>High-pass (0.1-0.45 Hz) - Tests high-frequency filtering
 *   <li>Band-pass (0.05-0.15 Hz) - Tests mid-range filtering
 * </ul>
 *
 * <p><strong>Updating Reference Images:</strong> When waveform rendering changes are intentional,
 * regenerate reference images by:
 *
 * <ol>
 *   <li>Temporarily modify test to generate new images
 *   <li>Verify new images are correct
 *   <li>Copy to src/test/resources/waveform-reference-images/
 *   <li>Restore test to comparison mode
 * </ol>
 */
@EnabledIf("isSampleFileAvailable")
class WaveformImageRegressionTest {
    private static final Logger logger = LoggerFactory.getLogger(WaveformImageRegressionTest.class);

    // Test configuration constants
    private static final String SAMPLE_FILE_PATH = "packaging/samples/sample.wav";
    private static final String OUTPUT_DIR = "build/test-results/waveform-images";
    private static final String REFERENCE_IMAGE_RESOURCE_PATH = "/waveform-reference-images/";
    private static final int TEST_CHUNK_COUNT = 3; // Number of 10-second chunks to test
    private static final int TEST_IMAGE_HEIGHT = 800; // Pixel height for generated images

    private FmodCore fmodCore;

    @BeforeEach
    void setUp() {
        // Initialize FmodCore with required dependencies (same pattern as FmodCoreTest)
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
    }

    @AfterEach
    void tearDown() {
        // Clean up FMOD resources if needed
        // Note: FmodCore manages its own lifecycle, but we ensure proper cleanup
        fmodCore = null;
    }

    @Test
    void testVisualWaveformRegressions() throws IOException {
        logger.info("Running waveform visual regression tests...");

        // Test different frequency ranges (staying well within MaryTTS bounds: 0 < freq < 0.5)
        assertImagesMatch("fullrange", 0.001, 0.45);
        assertImagesMatch("lowpass", 0.001, 0.1);
        assertImagesMatch("highpass", 0.1, 0.45);
        assertImagesMatch("bandpass", 0.05, 0.15);

        logger.info("All waveform visual regression tests passed!");
    }

    private void assertImagesMatch(String name, double minFreq, double maxFreq) throws IOException {
        logger.debug("Testing {} frequency range images ({}Hz - {}Hz)", name, minFreq, maxFreq);

        Waveform waveform = new Waveform(SAMPLE_FILE_PATH, minFreq, maxFreq, fmodCore);

        // Test configured number of chunks at standard height
        for (int chunkNum = 0; chunkNum < TEST_CHUNK_COUNT; chunkNum++) {
            Image actualImage = waveform.renderChunk(chunkNum, TEST_IMAGE_HEIGHT);
            String filename = String.format("%s_chunk%d.png", name, chunkNum);

            // Load reference image from test resources
            BufferedImage referenceImage = loadReferenceImage(filename);

            // Convert actual image to BufferedImage for comparison
            BufferedImage actualBuffered = toBufferedImage(actualImage);

            // Compare images using the library with pixel-perfect tolerance
            ImageComparison comparison =
                    new ImageComparison(referenceImage, actualBuffered)
                            .setThreshold(0) // 0 tolerance - pixel-perfect matching
                            .setRectangleLineWidth(1) // Thin highlight lines for differences
                            .setMinimalRectangleSize(1); // Highlight even single-pixel differences
            ImageComparisonResult result = comparison.compareImages();

            if (result.getImageComparisonState() != ImageComparisonState.MATCH) {
                // Save both the actual image and the diff image
                Path debugFile = Paths.get(OUTPUT_DIR, "debug_" + filename);
                Path diffFile = Paths.get(OUTPUT_DIR, "diff_" + filename);

                ImageIO.write(actualBuffered, "png", debugFile.toFile());
                ImageIO.write(result.getResult(), "png", diffFile.toFile());

                fail(
                        String.format(
                                "Visual regression detected in %s - expected pixel-perfect match"
                                        + " but found differences. Debug image: %s, Visual diff"
                                        + " highlights: %s",
                                filename, debugFile, diffFile));
            }

            logger.debug("✓ {}", filename);
        }
    }

    /**
     * Loads a reference image from test resources.
     *
     * @param filename the image filename (e.g., "fullrange_chunk0.png")
     * @return the loaded reference image
     * @throws IOException if the image cannot be found or loaded
     */
    private BufferedImage loadReferenceImage(String filename) throws IOException {
        String resourcePath = REFERENCE_IMAGE_RESOURCE_PATH + filename;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException(
                        "Reference image not found: "
                                + resourcePath
                                + ". Ensure reference images are generated and placed in"
                                + " src/test/resources/waveform-reference-images/");
            }
            return ImageIO.read(is);
        }
    }

    /** Converts an Image to BufferedImage for saving as PNG. */
    private BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }

        // Create a buffered image with transparency
        BufferedImage bufferedImage =
                new BufferedImage(
                        image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image onto the buffered image
        bufferedImage.createGraphics().drawImage(image, 0, 0, null);

        return bufferedImage;
    }

    /**
     * Condition method for JUnit @EnabledIf - only run if sample file exists.
     *
     * @return true if the sample audio file is available for testing
     */
    static boolean isSampleFileAvailable() {
        return Files.exists(Paths.get(SAMPLE_FILE_PATH));
    }
}
