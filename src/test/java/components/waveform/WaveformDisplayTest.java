package components.waveform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import annotation.Windowing;
import app.di.GuiceBootstrap;
import java.awt.Dimension;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import state.AudioState;

/**
 * Full application lifecycle test that verifies the waveform display updates its rendered height
 * when the component is resized. Uses the dev auto-loader to load the sample audio file.
 */
@Windowing
class WaveformDisplayTest {

    @BeforeEach
    void setUp() {
        // Enable dev autoload behavior inside the test JVM
        System.setProperty("app.run.dev", "true");
    }

    @AfterEach
    void tearDown() throws Exception {
        // Close all open windows to cleanly shut down the app after each test
        SwingUtilities.invokeAndWait(
                () -> {
                    for (Window w : Window.getWindows()) {
                        if (w.isDisplayable()) {
                            w.dispose();
                        }
                    }
                });
    }

    @Test
    @DisplayName("Waveform resizes with component height")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @EnabledIf("isSampleFileAvailable")
    void waveformResizesWithComponentHeight() throws Exception {
        // Optional visual hold divided across key moments
        long visualHold = Long.getLong("test.visualHoldMs", 0L);
        int holdSlices = 4;
        long holdSegment = visualHold > 0 ? Math.max(1L, visualHold / holdSlices) : 0L;

        // Start DI and application (publishes ApplicationStartedEvent)
        var bootstrap = GuiceBootstrap.create();
        SwingUtilities.invokeAndWait(bootstrap::startApplication);

        if (holdSegment > 0) Thread.sleep(holdSegment); // Observe initial app window

        // Acquire DI-managed services
        AudioState audioState = GuiceBootstrap.getInjectedInstance(AudioState.class);
        WaveformDisplay display = GuiceBootstrap.getInjectedInstance(WaveformDisplay.class);

        // Wait for audio to be opened by DevModeFileAutoLoader
        long start = System.currentTimeMillis();
        while (!audioState.audioOpen() && System.currentTimeMillis() - start < 5000) {
            Thread.sleep(50);
        }
        assertTrue(audioState.audioOpen(), "audio should be open via DevModeFileAutoLoader");

        // Wait until the display has produced its current chunk
        var curChunkField = WaveformDisplay.class.getDeclaredField("curRefreshChunk");
        curChunkField.setAccessible(true);
        start = System.currentTimeMillis();
        waveform.RenderedChunk curChunk = null;
        while (curChunk == null && System.currentTimeMillis() - start < 5000) {
            Object val = curChunkField.get(display);
            if (val instanceof waveform.RenderedChunk rc && rc.image() != null) {
                curChunk = rc;
                break;
            }
            Thread.sleep(50);
        }
        assertNotNull(curChunk, "waveform should render an initial chunk");

        if (holdSegment > 0) Thread.sleep(holdSegment); // Observe initial waveform render

        int initialCompHeight = display.getHeight();
        int initialImageHeight = curChunk.image().getHeight(null);
        assertEquals(
                initialCompHeight,
                initialImageHeight,
                "initial rendered image height should match component height");

        // Resize the top-level window so layout recalculates and the display height actually
        // changes according to the UI layout (rather than just the child component bounds)
        final int heightDelta = 160;
        final int widthDelta = 240;
        SwingUtilities.invokeAndWait(
                () -> {
                    Window window = SwingUtilities.getWindowAncestor(display);
                    if (window != null) {
                        window.setSize(
                                new Dimension(
                                        window.getWidth() + widthDelta,
                                        window.getHeight() + heightDelta));
                        window.validate();
                    }
                });

        if (holdSegment > 0) Thread.sleep(holdSegment); // Observe resized window before update

        // Wait for debounce and at least one refresh tick to update the image
        start = System.currentTimeMillis();
        int observedHeight = initialImageHeight;
        while (System.currentTimeMillis() - start < 4000) {
            Object val = curChunkField.get(display);
            if (val instanceof waveform.RenderedChunk rc && rc.image() != null) {
                observedHeight = rc.image().getHeight(null);
                // Stop when image height matches current component height
                if (observedHeight == display.getHeight()) {
                    break;
                }
            }
            Thread.sleep(50);
        }

        int expectedHeight = display.getHeight();
        assertTrue(
                expectedHeight > initialCompHeight,
                "display height should increase after frame resize");
        assertEquals(
                expectedHeight,
                observedHeight,
                "rendered image height should match display height after frame resize");

        if (holdSegment > 0) Thread.sleep(holdSegment); // Observe final state
    }

    /** Only run if sample file exists locally. */
    static boolean isSampleFileAvailable() {
        return Files.exists(Path.of("packaging/samples/sample.wav"));
    }
}
