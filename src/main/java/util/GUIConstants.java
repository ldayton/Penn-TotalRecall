package util;

import java.awt.Dimension;

/**
 * Central location for many kinds of constants used directly by GUI Components.
 *
 * <p>Constants other than those used by GUI components are stored in Constants.
 */
public class GUIConstants {

    // component sizes
    // use of MAX_VALUE is for the sake of particular layout manager's resizing behavior

    /** Size in pixels of bottom padding of ControlPanel. */
    public static final int controlPanelTopBorderWidth = 10;

    /** Size in pixels of top padding of ControlPanel. */
    public static final int controlPanelBottomBorderWidth = 15;

    /** Ideal size of WordPoolDisplay component. */
    public static final Dimension wordpoolDisplayDimension = new Dimension(250, Integer.MAX_VALUE);

    /** Ideal size of AnnotationDisplay component. */
    public static final Dimension annotationDisplayDimension =
            new Dimension(300, Integer.MAX_VALUE);

    /** Ideal size of SoundFileDisplay component. */
    public static final Dimension soundFileDisplayDimension = new Dimension(250, Integer.MAX_VALUE);

    // standard messages

    /** Title of all yes/no dialogs in the program. */
    public static final String yesNoDialogTitle = "Select an Option";

    /** Standard String asking whether or not user would like to see similar dialogs again. */
    public static final String dontShowAgainString = "Do not show this message again.";

    /** Title of all error dialogs in the program. */
    public static final String errorDialogTitle = "Error";

    // defaults always used at program startup

    /**
     * Width in pixels of visualization of one second of audio, prior to any zooming.
     *
     * <p>This is also used for the width of the time markings on the waveform.
     */
    public static final int zoomlessPixelsPerSecond = 200;

    /** Default position of volume slider knob, on 0-100 scale. */
    public static final int defaultSliderValue = 100;

    /** Number of pixels added/subtracted to zoomlessPixelsPerSecond for each zoom in/out action. */
    public static final int xZoomAmount = 40;

    /** The title of the <code>MainFrame</code> when audio is closed. */
    public static final String defaultFrameTitle =
            Constants.programName + " v" + Constants.programVersion;

    /** Private constructor to prevent instantiation. */
    private GUIConstants() {}
}
