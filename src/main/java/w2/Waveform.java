package w2;

import java.awt.Image;

/**
 * Viewport-aware waveform renderer.
 *
 * <p>Enables intelligent caching based on what's actually visible and scroll behavior.
 */
public interface Waveform {

    /**
     * Render waveform for viewport context.
     *
     * <p>Implementation can use viewport info to make intelligent caching decisions: - Cache size
     * based on viewport width, not arbitrary numbers - Prefetch direction based on scroll behavior
     * - Resolution based on zoom level
     */
    Image renderViewport(ViewportContext viewport);

    /** Create waveform for audio file. */
    static Waveform forAudioFile(String audioFilePath) {
        throw new UnsupportedOperationException("Implementation needed");
    }
}
