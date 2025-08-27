package components.waveform;

import java.awt.Component;
import java.awt.Rectangle;

/** Coordinate conversion between display pixels and audio frames for overlay rendering. */
public interface WaveformCoordinateSystem {
    /** Display area height in pixels. */
    int getHeight();

    /** Visible display rectangle for clipping calculations. */
    Rectangle getVisibleRect();

    /** Current playback progress bar X position. */
    int getProgressBarXPos();

    /** Convert display X pixel to audio frame number. */
    int displayXPixelToFrame(int xPix);

    /** Access as Component for Swing coordinate conversions. */
    Component asComponent();
}
