package w2;

import static org.junit.jupiter.api.Assertions.*;

import a2.AudioEngine;
import a2.AudioHandle;
import annotations.Audio;
import app.di.AppModule;
import com.github.romankh3.image.comparison.ImageComparison;
import com.github.romankh3.image.comparison.model.ImageComparisonResult;
import com.github.romankh3.image.comparison.model.ImageComparisonState;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pixel-for-pixel regression test for w2 waveform rendering, using real FMOD and sample.wav. */
@Audio
class WaveformImageRegressionTest {

    private static final String SAMPLE_WAV = "packaging/samples/sample.wav";
    private static final String EXPECTED_CLASSPATH =
            "/waveform-reference-images/bandpass_chunk0.png";

    private Injector injector;
    private AudioEngine engine;
    private Waveform waveform;

    @BeforeEach
    void setUp() {
        injector = Guice.createInjector(new AppModule());
        engine = injector.getInstance(AudioEngine.class);
    }

    @AfterEach
    void tearDown() {
        if (waveform instanceof WaveformImpl impl) {
            impl.shutdown();
        }
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("Renders chunk0 2000x800 matching reference image")
    void rendersImageMatchingReference() throws Exception {
        // Arrange: load the audio and create waveform
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);
        waveform = Waveform.forAudioFile(SAMPLE_WAV, engine, handle);

        // Viewport covering first 10 seconds at 200 px/sec => 2000x800
        ViewportContext viewport =
                new ViewportContext(
                        0.0, 10.0, 2000, 800, 200, ViewportContext.ScrollDirection.FORWARD);

        // Act: render, save actual image to disk before comparison
        Image img = waveform.renderViewport(viewport).get(15, TimeUnit.SECONDS);
        assertNotNull(img, "Renderer returned null image");
        BufferedImage actual =
                toBufferedImage(img, viewport.viewportWidthPx(), viewport.viewportHeightPx());

        Path outDir = Path.of("build", "waveform-regressions");
        Files.createDirectories(outDir);
        File actualFile = outDir.resolve("fullrange_chunk0_actual.png").toFile();
        ImageIO.write(actual, "png", actualFile);
        assertTrue(actualFile.exists() && actualFile.length() > 0, "Actual image not saved");

        // Load expected reference image from classpath
        BufferedImage expected = loadExpected(EXPECTED_CLASSPATH);
        assertEquals(2000, expected.getWidth(), "Expected image width mismatch");
        assertEquals(800, expected.getHeight(), "Expected image height mismatch");

        // Compare pixel-for-pixel
        ImageComparison comparison = new ImageComparison(expected, actual);
        // For exact match; adjust if anti-aliasing differences occur
        comparison.setPixelToleranceLevel(0.0);
        ImageComparisonResult result = comparison.compareImages();

        if (result.getImageComparisonState() != ImageComparisonState.MATCH) {
            // Save diff image to help debugging
            File diffFile = outDir.resolve("fullrange_chunk0_diff.png").toFile();
            ImageIO.write(result.getResult(), "png", diffFile);
        }

        assertEquals(
                ImageComparisonState.MATCH,
                result.getImageComparisonState(),
                "Rendered image differs from reference. See build/waveform-regressions for"
                        + " outputs.");
    }

    private static BufferedImage toBufferedImage(Image img, int width, int height) {
        if (img instanceof BufferedImage bi) {
            return bi;
        }
        BufferedImage converted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        converted.getGraphics().drawImage(img, 0, 0, null);
        converted.getGraphics().dispose();
        return converted;
    }

    private static BufferedImage loadExpected(String classpathResource) throws IOException {
        URL url = WaveformImageRegressionTest.class.getResource(classpathResource);
        Objects.requireNonNull(url, "Missing expected reference: " + classpathResource);
        return ImageIO.read(url);
    }
}
