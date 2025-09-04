package ui.waveform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import annotations.Windowing;
import app.di.GuiceBootstrap;
import java.awt.Dimension;
import java.awt.Window;
import java.util.concurrent.TimeUnit;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import state.AudioState;
import ui.MainFrame;

/**
 * Full application lifecycle test that verifies the waveform display updates its rendered height
 * when the component is resized. Uses the dev auto-loader to load the sample audio file.
 */
@Windowing
@annotations.AudioEngine
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

    // Sample file is part of the repo; tests always assume it exists

    @Test
    @DisplayName("Zoom In increases chunk image width")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void zoomInIncreasesImageWidth() throws Exception {
        long visualHold = Long.getLong("test.visualHoldMs", 0L);
        int holdSlices = 4;
        long holdSegment = visualHold > 0 ? Math.max(1L, visualHold / holdSlices) : 0L;

        var bootstrap = GuiceBootstrap.create();
        SwingUtilities.invokeAndWait(bootstrap::startApplication);

        if (holdSegment > 0) Thread.sleep(holdSegment); // Observe initial app window

        AudioState audioState = GuiceBootstrap.getInjectedInstance(AudioState.class);
        WaveformDisplay display = GuiceBootstrap.getInjectedInstance(WaveformDisplay.class);

        long start = System.currentTimeMillis();
        while (!audioState.audioOpen() && System.currentTimeMillis() - start < 5000) {
            Thread.sleep(50);
        }
        assertTrue(audioState.audioOpen(), "audio should be open via DevModeFileAutoLoader");

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

        int initialWidth = curChunk.image().getWidth(null);
        int expectedWidth = initialWidth + WaveformDisplay.ZOOM_AMOUNT * 10; // chunk is 10s wide

        // Trigger Zoom In via the View menu item
        SwingUtilities.invokeAndWait(
                () -> {
                    MainFrame frame = GuiceBootstrap.getInjectedInstance(MainFrame.class);
                    JMenuBar menuBar = frame.getJMenuBar();
                    JMenu viewMenu = null;
                    for (int i = 0; i < menuBar.getMenuCount(); i++) {
                        JMenu m = menuBar.getMenu(i);
                        if (m != null && "View".equals(m.getText())) {
                            viewMenu = m;
                            break;
                        }
                    }
                    if (viewMenu == null) throw new IllegalStateException("View menu not found");
                    JMenuItem zoomInItem = null;
                    for (int i = 0; i < viewMenu.getItemCount(); i++) {
                        JMenuItem it = viewMenu.getItem(i);
                        if (it != null && "Zoom In".equals(it.getText())) {
                            zoomInItem = it;
                            break;
                        }
                    }
                    if (zoomInItem == null) throw new IllegalStateException("Zoom In not found");
                    zoomInItem.doClick();
                });

        if (holdSegment > 0) Thread.sleep(holdSegment); // Observe post-zoom state before update

        start = System.currentTimeMillis();
        int observedWidth = initialWidth;
        while (System.currentTimeMillis() - start < 4000) {
            Object val = curChunkField.get(display);
            if (val instanceof waveform.RenderedChunk rc && rc.image() != null) {
                observedWidth = rc.image().getWidth(null);
                if (observedWidth == expectedWidth) {
                    break;
                }
            }
            Thread.sleep(50);
        }

        assertEquals(
                expectedWidth, observedWidth, "zoom in should increase image width by 10*xZoom");

        if (holdSegment > 0) Thread.sleep(holdSegment); // Observe final state
    }

    @Test
    @DisplayName("Zoom Out decreases chunk image width")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void zoomOutDecreasesImageWidth() throws Exception {
        long visualHold = Long.getLong("test.visualHoldMs", 0L);
        int holdSlices = 4;
        long holdSegment = visualHold > 0 ? Math.max(1L, visualHold / holdSlices) : 0L;

        var bootstrap = GuiceBootstrap.create();
        SwingUtilities.invokeAndWait(bootstrap::startApplication);

        if (holdSegment > 0) Thread.sleep(holdSegment); // Observe initial app window

        AudioState audioState = GuiceBootstrap.getInjectedInstance(AudioState.class);
        WaveformDisplay display = GuiceBootstrap.getInjectedInstance(WaveformDisplay.class);

        long start = System.currentTimeMillis();
        while (!audioState.audioOpen() && System.currentTimeMillis() - start < 5000) {
            Thread.sleep(50);
        }
        assertTrue(audioState.audioOpen(), "audio should be open via DevModeFileAutoLoader");

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

        int initialWidth = curChunk.image().getWidth(null);
        int expectedWidth = initialWidth - WaveformDisplay.ZOOM_AMOUNT * 10; // chunk is 10s wide
        assertTrue(expectedWidth > 0, "expected width must be positive");

        // Trigger Zoom Out via the View menu item
        SwingUtilities.invokeAndWait(
                () -> {
                    MainFrame frame = GuiceBootstrap.getInjectedInstance(MainFrame.class);
                    JMenuBar menuBar = frame.getJMenuBar();
                    JMenu viewMenu = null;
                    for (int i = 0; i < menuBar.getMenuCount(); i++) {
                        JMenu m = menuBar.getMenu(i);
                        if (m != null && "View".equals(m.getText())) {
                            viewMenu = m;
                            break;
                        }
                    }
                    if (viewMenu == null) throw new IllegalStateException("View menu not found");
                    JMenuItem zoomOutItem = null;
                    for (int i = 0; i < viewMenu.getItemCount(); i++) {
                        JMenuItem it = viewMenu.getItem(i);
                        if (it != null && "Zoom Out".equals(it.getText())) {
                            zoomOutItem = it;
                            break;
                        }
                    }
                    if (zoomOutItem == null) throw new IllegalStateException("Zoom Out not found");
                    zoomOutItem.doClick();
                });

        if (holdSegment > 0) Thread.sleep(holdSegment); // Observe post-zoom state before update

        start = System.currentTimeMillis();
        int observedWidth = initialWidth;
        while (System.currentTimeMillis() - start < 4000) {
            Object val = curChunkField.get(display);
            if (val instanceof waveform.RenderedChunk rc && rc.image() != null) {
                observedWidth = rc.image().getWidth(null);
                if (observedWidth == expectedWidth) {
                    break;
                }
            }
            Thread.sleep(50);
        }

        assertEquals(
                expectedWidth, observedWidth, "zoom out should decrease image width by 10*xZoom");

        if (holdSegment > 0) Thread.sleep(holdSegment); // Observe final state
    }
}
