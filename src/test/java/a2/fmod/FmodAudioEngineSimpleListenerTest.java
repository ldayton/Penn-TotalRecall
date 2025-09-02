package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;

import a2.AudioHandle;
import a2.PlaybackHandle;
import a2.PlaybackListener;
import a2.PlaybackState;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FmodAudioEngineSimpleListenerTest {

    @Test
    void testListenerManagement() {
        try (FmodAudioEngine engine = new FmodAudioEngine()) {
            // Test adding listeners
            PlaybackListener listener1 = new PlaybackListener() {};
            PlaybackListener listener2 = new PlaybackListener() {};

            engine.addPlaybackListener(listener1);
            engine.addPlaybackListener(listener2);

            // Test removing listeners
            engine.removePlaybackListener(listener1);
            engine.removePlaybackListener(listener2);

            // No exceptions should be thrown
            assertTrue(true);
        }
    }

    @Test
    void testListenerCallbackPattern() throws InterruptedException {
        // This test demonstrates the expected usage pattern
        CountDownLatch progressLatch = new CountDownLatch(1);
        AtomicBoolean completeCalled = new AtomicBoolean(false);
        AtomicInteger progressCount = new AtomicInteger(0);

        PlaybackListener listener =
                new PlaybackListener() {
                    @Override
                    public void onProgress(
                            PlaybackHandle playback, long positionFrames, long totalFrames) {
                        progressCount.incrementAndGet();
                        progressLatch.countDown();
                    }

                    @Override
                    public void onStateChanged(
                            PlaybackHandle playback,
                            PlaybackState newState,
                            PlaybackState oldState) {
                        System.out.println("State changed from " + oldState + " to " + newState);
                    }

                    @Override
                    public void onPlaybackComplete(PlaybackHandle playback) {
                        completeCalled.set(true);
                    }
                };

        // Simulate callbacks with test handle
        PlaybackHandle testHandle = new TestPlaybackHandle();
        listener.onProgress(testHandle, 100, 1000);
        listener.onStateChanged(testHandle, PlaybackState.PLAYING, PlaybackState.STOPPED);
        listener.onPlaybackComplete(testHandle);

        // Verify callbacks were received
        assertTrue(progressLatch.await(100, TimeUnit.MILLISECONDS));
        assertEquals(1, progressCount.get());
        assertTrue(completeCalled.get());
    }

    @Test
    void testDefaultMethodImplementation() {
        // Test that default methods don't require implementation
        PlaybackListener minimalListener = new PlaybackListener() {
                    // No methods overridden - should use defaults
                };

        // These should all work without throwing exceptions
        // Most methods require non-null playback handle
        PlaybackHandle testHandle = new TestPlaybackHandle();
        minimalListener.onProgress(testHandle, 0, 100);
        minimalListener.onStateChanged(testHandle, PlaybackState.PLAYING, PlaybackState.STOPPED);
        minimalListener.onPlaybackComplete(testHandle);
        minimalListener.onPlaybackError(null, "test error"); // This one allows null

        // No exceptions should be thrown
        assertTrue(true);
    }

    // Simple test implementation of PlaybackHandle
    private static class TestPlaybackHandle implements PlaybackHandle {
        @Override
        public long getId() {
            return 12345L;
        }

        @Override
        public AudioHandle getAudioHandle() {
            return null; // Not needed for listener tests
        }

        @Override
        public long getStartFrame() {
            return 0;
        }

        @Override
        public long getEndFrame() {
            return 44100; // 1 second at 44.1kHz
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void close() {
            // No-op for test
        }
    }
}
