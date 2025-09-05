package ui;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;
import javax.swing.UIManager;
import lombok.extern.slf4j.Slf4j;

/**
 * A clean canvas for waveform rendering. Unlike WaveformDisplay, this component has no internal
 * state about audio timing or position. It simply provides a surface for painting and reports its
 * dimensions.
 */
@Singleton
@Slf4j
public class WaveformCanvas extends JComponent {

    private static final Color BACKGROUND_COLOR = UIManager.getColor("Panel.background");

    @Inject
    public WaveformCanvas() {
        setOpaque(true);
        setBackground(BACKGROUND_COLOR);
        setPreferredSize(new Dimension(800, 200));
        log.debug("WaveformCanvas created");
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // For now, just paint the background
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());

        // Placeholder text to show it's working
        g.setColor(Color.GRAY);
        String message = "WaveformCanvas";
        int x = (getWidth() - g.getFontMetrics().stringWidth(message)) / 2;
        int y = getHeight() / 2;
        g.drawString(message, x, y);
    }
}
