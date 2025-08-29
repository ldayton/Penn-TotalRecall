package ui;

import java.awt.BasicStroke;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;

/**
 * Central location for <code>Strokes</code>, <code>Borders</code>, and <code>RenderingHints</code>
 * in the GUI.
 *
 * <p>Objects are created on the first call of their getter.
 */
public class UiShapes {

    private static BasicStroke progressBarStroke;

    private static RenderingHints renderHints;

    // Strokes
    /**
     * Getter for the <code>Stroke</code> used by the selection bar.
     *
     * <p>Creates the <code>Stroke</code> on the first call.
     *
     * @return The <code>Stroke</code> for the selection bar
     */
    public static BasicStroke getProgressBarStroke() {
        if (progressBarStroke == null) {
            progressBarStroke =
                    new BasicStroke(
                            1,
                            BasicStroke.CAP_SQUARE,
                            BasicStroke.JOIN_BEVEL,
                            0,
                            new float[] {10.0f, 3.0f},
                            0); // float array specifies 10 pixels on, 3 off
        }
        return progressBarStroke;
    }

    // Borders

    /**
     * Creates a titled border using FlatLaf's built-in styling.
     *
     * @param title The border title
     * @return The constructed <code>Border</code>
     */
    public static TitledBorder createMyUnfocusedTitledBorder(String title) {
        return BorderFactory.createTitledBorder(title);
    }

    // RenderingHints

    /**
     * Getter for a <code>RenderingHints</code> object that makes Fonts and Lines more attractive by
     * turning on anti-aliasing and high-quality rendering.
     *
     * <p>Creates the <code>RenderingHints</code> on the first call.
     *
     * @return The <code>RenderingHints</code> object with attractive settings on
     */
    public static RenderingHints getRenderingHints() {
        if (renderHints == null) {
            renderHints =
                    new RenderingHints(
                            RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            renderHints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        }
        return renderHints;
    }

    /** Private constructor to prevent instantiation. */
    private UiShapes() {}
}
