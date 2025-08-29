package ui;

import java.awt.Color;

/** Central location for colors in the GUI. */
public class UiColors {

    // component colors

    /** Color of background behind waveform. */
    public static final Color waveformBackground = Color.WHITE;

    // text colors

    /** Color of annotation text on waveform. */
    public static final Color annotationTextColor = Color.BLACK;

    // line/bar colors

    /**
     * Color of vertical reference line that indicates on the waveform the current position of the
     * audio playback.
     */
    public static final Color progressBarColor = Color.BLACK;

    /** Color of horizontal reference line cutting through waveform. */
    public static final Color waveformReferenceLineColor = Color.BLACK;

    public static final Color annotationAccentColor = Color.BLUE;

    /**
     * Color of vertical reference lines and associated strings that indicate on he waveform
     * annotations.
     */
    public static final Color annotationLineColor = Color.RED; // PyParse color (255, 64, 255)

    public static final Color replay200MillisFlashColor =
            new Color(
                    120, 172,
                    221); // mac push button focused color, better than SystemColor.controlHighlight
    // for other OSes

    public static final Color mouseHighlightColor =
            new Color(
                    120, 172,
                    221); // mac push button focused color, better than SystemColor.controlHighlight

    // for other OSes

    // available in WindowsLAF: UIManager.getDefaults().getColor("textHighlight");

    /** Private constructor to prevent instantiation. */
    private UiColors() {}
}
