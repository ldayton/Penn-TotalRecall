package components.waveform;

import java.awt.Component;
import java.awt.Rectangle;

/** Interface defining geometric operations needed by overlay components. */
public interface WaveformGeometry {
    /** Returns the height of the waveform display area. */
    int getHeight();

    /** Returns the visible rectangle of the waveform display. */
    Rectangle getVisibleRect();

    /** Returns the current progress bar X position. */
    int getProgressBarXPos();

    /** Converts display X pixel coordinate to audio frame number. */
    int displayXPixelToFrame(int xPix);

    /** Returns this geometry provider as a Component for coordinate conversion. */
    Component asComponent();
}
