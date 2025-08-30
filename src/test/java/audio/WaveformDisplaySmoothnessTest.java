package audio;

import static org.junit.jupiter.api.Assertions.*;

import annotation.Windowing;
import app.Main;
import app.di.GuiceBootstrap;
import events.AudioEvent;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import state.AudioState;

@DisplayName("Waveform Smoothness Integration")
@AudioHardware
@Windowing
class WaveformDisplaySmoothnessTest {
    private static final Logger logger =
            LoggerFactory.getLogger(WaveformDisplaySmoothnessTest.class);

    private static final int APP_STARTUP_TIMEOUT_SECONDS = 30;
    private static final int PLAYBACK_TEST_DURATION_SECONDS = 10;
    private static final int TEST_TIMEOUT_SECONDS = 75;

    @Test
    @DisplayName("waveform scrolling has smooth progress callbacks during full app playback")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void waveformScrollingHasSmoothProgressCallbacks() throws Exception {
        logger.info("🚀 Starting full application lifecycle test for waveform smoothness...");

        File testFile = new File("packaging/samples/sample.wav");
        assertTrue(testFile.exists(), "Test audio file must exist: " + testFile.getAbsolutePath());

        // Start the complete application
        logger.info("Starting main application...");
        CompletableFuture.runAsync(
                () -> {
                    try {
                        Main.main(new String[0]);
                    } catch (Exception e) {
                        logger.error("Application startup failed", e);
                        throw new RuntimeException("Application startup failed", e);
                    }
                });

        // Wait for application to fully start
        boolean appStarted = waitForApplicationToStart(APP_STARTUP_TIMEOUT_SECONDS);
        assertTrue(
                appStarted,
                "Application should start within " + APP_STARTUP_TIMEOUT_SECONDS + " seconds");
        logger.info("✅ Application started successfully");

        try {
            // Get application components through DI
            AudioState audioState = GuiceBootstrap.getInjectedInstance(AudioState.class);
            assertNotNull(audioState, "AudioState should be available");

            // Open the test audio file through AudioState
            logger.info("Opening test audio file: {}", testFile.getName());
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            ui.audiofiles.AudioFile audioFile =
                                    new ui.audiofiles.AudioFile(testFile.getAbsolutePath());
                            audioState.switchFile(audioFile);
                            assertTrue(
                                    audioState.audioOpen(),
                                    "Audio file should be opened successfully");
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to open audio file", e);
                        }
                    });
            logger.info("✅ Audio file opened successfully");

            // Give the window time to fully render the waveform before starting playback
            logger.info("Waiting for UI rendering to stabilize...");
            Thread.sleep(1500);
            logger.info("✅ UI rendering stabilized");

            // Calculate a start frame from actual UI state (viewport width and current scale)
            final long[] scrollingStartFrameBox = {0L};
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            ui.waveform.WaveformDisplay display =
                                    GuiceBootstrap.getInjectedInstance(
                                            ui.waveform.WaveformDisplay.class);
                            int width = display.getWidth();
                            // Reflect pixelsPerSecond from WaveformDisplay for accurate conversion
                            int pxPerSec;
                            try {
                                var f =
                                        ui.waveform.WaveformDisplay.class.getDeclaredField(
                                                "pixelsPerSecond");
                                f.setAccessible(true);
                                pxPerSec = (int) f.get(display);
                            } catch (Exception ex) {
                                pxPerSec = 200; // fallback to default constant
                            }
                            int sr = (int) Math.round(audioState.getCalculator().sampleRate());
                            double framesPerPixel = sr / (double) pxPerSec;
                            long visibleFrames =
                                    (long) Math.max(1, Math.round(width * framesPerPixel));
                            scrollingStartFrameBox[0] = Math.max(0, visibleFrames / 2);
                            logger.info(
                                    "Computed visible frames ~{} (width={}, px/s={})",
                                    visibleFrames,
                                    width,
                                    pxPerSec);
                        } catch (Exception e) {
                            // Default to 1/4 of the file length if UI probing fails
                            long total = audioState.getCalculator().durationInFrames();
                            scrollingStartFrameBox[0] = Math.max(0, total / 4);
                        }
                    });
            long scrollingStartFrame = scrollingStartFrameBox[0];

            // Get file duration and ensure we don't exceed it
            long totalFrames = audioState.getCalculator().durationInFrames();
            int sampleRate = (int) Math.round(audioState.getCalculator().sampleRate());
            long testDurationFrames = (long) (sampleRate * PLAYBACK_TEST_DURATION_SECONDS);
            long testEndFrame = Math.min(scrollingStartFrame + testDurationFrames, totalFrames - 1);

            if (testEndFrame <= scrollingStartFrame) {
                // File too short, start earlier
                scrollingStartFrame = Math.max(0, totalFrames - testDurationFrames - 1);
                testEndFrame = Math.min(scrollingStartFrame + testDurationFrames, totalFrames - 1);
            }

            long finalScrollingStartFrame = scrollingStartFrame;
            long finalTestEndFrame = testEndFrame;

            // Prepare collectors
            var collector = new ProgressCallbackCollector();
            // Enable in-component paint tracking and EDT heartbeat
            System.setProperty("test.waveform.trackPaint", "true");
            ui.waveform.WaveformDisplayTestProbe.clear();
            var heartbeat = new EdtHeartbeat();
            heartbeat.start();
            AudioPlayer audioPlayer = audioState.getPlayer();

            logger.info("Starting playback for smoothness measurement...");
            long testStartTime = System.nanoTime();

            SwingUtilities.invokeLater(
                    () -> {
                        audioPlayer.playAt(finalScrollingStartFrame, finalTestEndFrame);
                    });

            // Wait for playback to complete or timeout
            CountDownLatch playbackDone = new CountDownLatch(1);
            AudioEvent.Listener listener =
                    new AudioEvent.Listener() {
                        @Override
                        public void onProgress(long frame) {
                            collector.onProgress(frame);
                        }

                        @Override
                        public void onEvent(AudioEvent event) {
                            if (event.type() == AudioEvent.Type.EOM
                                    || event.type() == AudioEvent.Type.STOPPED) {
                                playbackDone.countDown();
                            }
                        }
                    };
            audioPlayer.addListener(listener);

            boolean completed =
                    playbackDone.await(PLAYBACK_TEST_DURATION_SECONDS + 10, TimeUnit.SECONDS);
            assertTrue(completed, "Playback should complete within expected time");

            long testEndTime = System.nanoTime();
            logger.info("✅ Playback completed, analyzing smoothness...");

            // Analyze and assert smoothness metrics
            collector.assertAndPrintStatistics(
                    testStartTime,
                    testEndTime,
                    finalScrollingStartFrame,
                    finalTestEndFrame,
                    sampleRate);

            // Cleanup listeners/collectors
            audioPlayer.removeListener(listener);
            heartbeat.stop();

        } finally {
            // Clean shutdown of application
            logger.info("Shutting down application...");
            SwingUtilities.invokeLater(
                    () -> {
                        for (Window window : Window.getWindows()) {
                            if (window.isDisplayable()) {
                                window.dispose();
                            }
                        }
                    });

            // Brief wait for cleanup
            Thread.sleep(1000);
        }

        logger.info("🎉 Waveform smoothness integration test completed successfully");
    }

    private boolean waitForApplicationToStart(int timeoutSeconds) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                // Check if GUI is visible
                final boolean[] guiVisible = {false};
                SwingUtilities.invokeAndWait(
                        () -> {
                            for (Window window : Window.getWindows()) {
                                if (window.isDisplayable() && window.isVisible()) {
                                    guiVisible[0] = true;
                                    return;
                                }
                            }
                        });

                // Check if DI is ready (basic smoke test)
                if (guiVisible[0]) {
                    try {
                        GuiceBootstrap.getInjectedInstance(AudioState.class);
                        return true; // Both GUI and DI are ready
                    } catch (Exception e) {
                        // DI not ready yet, keep waiting
                    }
                }
            } catch (Exception e) {
                // Still initializing, keep waiting
            }

            Thread.sleep(500);
        }

        return false;
    }

    private static class ProgressCallbackCollector implements AudioEvent.Listener {
        private final List<ProgressSample> samples = new ArrayList<>();
        private final Object lock = new Object();

        private static class ProgressSample {
            final long framePosition;
            final long timestampNanos;

            ProgressSample(long framePosition, long timestampNanos) {
                this.framePosition = framePosition;
                this.timestampNanos = timestampNanos;
            }
        }

        @Override
        public void onProgress(long frame) {
            synchronized (lock) {
                samples.add(new ProgressSample(frame, System.nanoTime()));
            }
        }

        @Override
        public void onEvent(AudioEvent event) {
            // Event handling not needed for smoothness analysis
        }

        void assertAndPrintStatistics(
                long testStartNanos,
                long testEndNanos,
                long startFrame,
                long endFrame,
                int sampleRate) {
            synchronized (lock) {
                if (samples.isEmpty()) {
                    logger.warn("=== NO PROGRESS CALLBACKS RECEIVED ===");
                    fail("No progress callbacks received");
                }

                logger.info("=== Waveform Progress Smoothness Statistics ===");

                double testDurationSec = (testEndNanos - testStartNanos) / 1_000_000_000.0;
                logger.info("Total callbacks: {}", samples.size());
                logger.info(String.format("Test duration: %.2f seconds", testDurationSec));
                logger.info(
                        "Frame range: {} -> {} ({} frames)",
                        startFrame,
                        endFrame,
                        endFrame - startFrame);

                List<Double> intervalsMs = new ArrayList<>();
                for (int i = 1; i < samples.size(); i++) {
                    long deltaNanos =
                            samples.get(i).timestampNanos - samples.get(i - 1).timestampNanos;
                    intervalsMs.add(deltaNanos / 1_000_000.0);
                }

                if (!intervalsMs.isEmpty()) {
                    double meanInterval =
                            intervalsMs.stream()
                                    .mapToDouble(Double::doubleValue)
                                    .average()
                                    .orElse(0.0);
                    double targetInterval = 1000.0 / 30.0;
                    double actualFrequency = samples.size() / testDurationSec;

                    logger.info(
                            String.format(
                                    "Mean interval: %.1fms (target: %.1fms)",
                                    meanInterval, targetInterval));
                    logger.info(
                            String.format(
                                    "Actual frequency: %.1f Hz (target: 30Hz)", actualFrequency));

                    double minInterval =
                            intervalsMs.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                    double maxInterval =
                            intervalsMs.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                    logger.info(
                            String.format(
                                    "Min interval: %.1fms, Max interval: %.1fms",
                                    minInterval, maxInterval));

                    double variance =
                            intervalsMs.stream()
                                    .mapToDouble(interval -> Math.pow(interval - meanInterval, 2))
                                    .average()
                                    .orElse(0.0);
                    double stdDev = Math.sqrt(variance);
                    logger.info(String.format("Std deviation: %.1fms", stdDev));

                    long largeGaps =
                            intervalsMs.stream()
                                    .mapToLong(interval -> interval > 100.0 ? 1 : 0)
                                    .sum();
                    logger.info("Large gaps (>100ms): {}", largeGaps);

                    assertTrue(
                            actualFrequency >= 20.0 && actualFrequency <= 45.0,
                            String.format(
                                    "Callback frequency %.1f Hz out of bounds", actualFrequency));
                    assertTrue(
                            meanInterval >= 20.0 && meanInterval <= 50.0,
                            String.format("Mean interval %.1f ms out of bounds", meanInterval));
                    assertTrue(
                            maxInterval < 150.0,
                            String.format("Max interval %.1f ms too high", maxInterval));
                    assertTrue(largeGaps <= 2, "Too many large gaps (>100ms): " + largeGaps);
                }

                boolean monotonic = true;
                for (int i = 1; i < samples.size(); i++) {
                    if (samples.get(i).framePosition < samples.get(i - 1).framePosition) {
                        monotonic = false;
                        break;
                    }
                }
                logger.info(
                        "Frame monotonicity: {}",
                        monotonic ? "PASS" : "FAIL (backwards motion detected)");
                assertTrue(monotonic, "Frame position moved backwards");

                if (samples.size() >= 2) {
                    long firstFrame = samples.get(0).framePosition;
                    long lastFrame = samples.get(samples.size() - 1).framePosition;
                    long frameProgress = lastFrame - firstFrame;
                    logger.info(
                            "Frame progression: {} -> {} ({} frames)",
                            firstFrame,
                            lastFrame,
                            frameProgress);
                    long expectedFrames =
                            (long) (sampleRate * (testEndNanos - testStartNanos) / 1_000_000_000.0);
                    long lower = (long) Math.floor(expectedFrames * 0.80);
                    long upper = (long) Math.ceil(expectedFrames * 1.20);
                    assertTrue(
                            frameProgress >= lower && frameProgress <= upper,
                            String.format(
                                    "Frame progression %d outside expected [%d, %d]",
                                    frameProgress, lower, upper));
                }

                // Paint cadence assertions
                List<Long> paintTimes = ui.waveform.WaveformDisplayTestProbe.getTimesCopy();
                assertFalse(paintTimes.isEmpty(), "No paint events captured for WaveformDisplay");
                List<Double> paintIntervals = new ArrayList<>();
                for (int i = 1; i < paintTimes.size(); i++) {
                    paintIntervals.add((paintTimes.get(i) - paintTimes.get(i - 1)) / 1_000_000.0);
                }
                paintIntervals.sort(Double::compareTo);
                double paintMax = paintIntervals.get(paintIntervals.size() - 1);
                double paintP95 = percentile(paintIntervals, 95.0);
                logger.info(
                        String.format(
                                "Paint intervals: p95=%.1fms, max=%.1fms (N=%d)",
                                paintP95, paintMax, paintIntervals.size()));
                long stutters = paintIntervals.stream().filter(v -> v > 66.0).count();
                logger.info("Paint stutters (>66ms): {}", stutters);
                assertTrue(paintP95 <= 70.0, String.format("Paint p95 too high: %.1fms", paintP95));
                assertTrue(paintMax < 120.0, String.format("Paint max too high: %.1fms", paintMax));
                assertTrue(stutters <= 2, "Too many paint stutters: " + stutters);

                // Heartbeat (EDT latency) assertions
                List<Double> hb = EdtHeartbeat.getLatenciesMsCopy();
                assertFalse(hb.isEmpty(), "No EDT heartbeat samples captured");
                hb.sort(Double::compareTo);
                double hbMax = hb.get(hb.size() - 1);
                double hbP95 = percentile(hb, 95.0);
                logger.info(
                        String.format(
                                "EDT heartbeat: p95=%.1fms, max=%.1fms (N=%d)",
                                hbP95, hbMax, hb.size()));
                long hbStutters = hb.stream().filter(v -> v > 66.0).count();
                logger.info("EDT stutters (>66ms): {}", hbStutters);
                assertTrue(hbP95 <= 70.0, String.format("EDT p95 too high: %.1fms", hbP95));
                assertTrue(hbMax < 120.0, String.format("EDT max too high: %.1fms", hbMax));
                assertTrue(hbStutters <= 2, "Too many EDT stutters: " + hbStutters);

                logger.info("=== End Statistics ===");
            }
        }

        private static double percentile(List<Double> sortedValuesAsc, double pct) {
            if (sortedValuesAsc.isEmpty()) return 0.0;
            double rank = (pct / 100.0) * (sortedValuesAsc.size() - 1);
            int lo = (int) Math.floor(rank);
            int hi = (int) Math.ceil(rank);
            if (lo == hi) return sortedValuesAsc.get(lo);
            double w = rank - lo;
            return sortedValuesAsc.get(lo) * (1 - w) + sortedValuesAsc.get(hi) * w;
        }
    }

    // Paint events captured via WaveformDisplayTestProbe

    /** Posts periodic Runnables to EDT and measures latency. */
    private static class EdtHeartbeat {
        private static final List<Double> LAT_MS = new ArrayList<>();
        private static final Object LOCK = new Object();
        private volatile boolean running = false;
        private Thread worker;

        void start() {
            running = true;
            worker =
                    new Thread(
                            () -> {
                                try {
                                    while (running) {
                                        long posted = System.nanoTime();
                                        SwingUtilities.invokeLater(
                                                () -> {
                                                    double ms =
                                                            (System.nanoTime() - posted)
                                                                    / 1_000_000.0;
                                                    synchronized (LOCK) {
                                                        LAT_MS.add(ms);
                                                    }
                                                });
                                        Thread.sleep(33);
                                    }
                                } catch (InterruptedException ignored) {
                                }
                            },
                            "EDT-Heartbeat");
            worker.setDaemon(true);
            worker.start();
        }

        void stop() {
            running = false;
            if (worker != null) worker.interrupt();
        }

        static List<Double> getLatenciesMsCopy() {
            synchronized (LOCK) {
                return new ArrayList<>(LAT_MS);
            }
        }
    }
}
