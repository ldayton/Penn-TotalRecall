package ui.layout;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;

/** Small UI style helpers for consistent look-and-feel. */
public final class UiStyles {
    private UiStyles() {}

    /**
     * Returns a subtle rounded line border with uniform inner padding.
     *
     * @param paddingPx inner padding in pixels
     */
    public static Border roundedBox(int paddingPx) {
        Color c = UIManager.getColor("Separator.foreground");
        Border line = BorderFactory.createLineBorder(c, 1, true);
        Border pad = BorderFactory.createEmptyBorder(paddingPx, paddingPx, paddingPx, paddingPx);
        return BorderFactory.createCompoundBorder(line, pad);
    }
}
