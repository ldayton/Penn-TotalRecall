package ui.layout;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import ui.DoneButton;
import ui.annotations.AnnotationDisplay;
import ui.audiofiles.AudioFileDisplay;
import ui.wordpool.WordpoolDisplay;

/**
 * Custom <code>JPanel</code> that is used as the bottom half of <code>ContentSplitPane</code>,
 * containing control components such as wordpool display, file list, etc.
 */
@Singleton
public class ControlPanel extends JPanel {

    private static final int H_GAP_PX = 16;
    private static final int OUTER_PADDING_PX = 12;
    private static final int LABEL_SPACING_PX = 6;

    /** Creates a new instance, initializing listeners and appearance. */
    @Inject
    public ControlPanel(
            DoneButton doneButton,
            AudioFileDisplay audioFileDisplay,
            AnnotationDisplay annotationDisplay,
            WordpoolDisplay wordpoolDisplay) {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        // Wrap each control in a vertical stack: label above, box below
        var audioSection = createSection("Audio Files", audioFileDisplay);
        var wordpoolSection = createSection("Wordpool", wordpoolDisplay);
        var annotationSection = createSection("Annotations", annotationDisplay);

        // Compact, even gaps between sections
        add(Box.createRigidArea(new Dimension(H_GAP_PX, 0)));
        add(audioSection);
        add(Box.createRigidArea(new Dimension(H_GAP_PX, 0)));
        add(wordpoolSection);
        add(Box.createRigidArea(new Dimension(H_GAP_PX, 0)));
        add(annotationSection);
        add(Box.createRigidArea(new Dimension(H_GAP_PX, 0)));
        add(doneButton);
        add(Box.createRigidArea(new Dimension(H_GAP_PX, 0)));
        // Glue at end keeps button pushed right while sections stay left-aligned
        add(Box.createHorizontalGlue());

        // since ControlPanel is a clickable area, we must write focus handling code for the event
        // it is clicked on
        // passes focus to frame
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        getParent().requestFocusInWindow();
                    }
                });

        // Subtle separation from waveform above
        setBorder(
                BorderFactory.createEmptyBorder(
                        OUTER_PADDING_PX, OUTER_PADDING_PX, OUTER_PADDING_PX, OUTER_PADDING_PX));
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(UIManager.getColor("Separator.foreground"));
        g.drawLine(0, 0, getWidth() - 1, 0);
    }

    private JPanel createSection(String title, JComponent content) {
        var panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        var label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        // Left-align label to the component edge
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, LABEL_SPACING_PX, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);

        // Ensure the content is also left-aligned within the section
        content.setAlignmentX(LEFT_ALIGNMENT);

        panel.add(label);
        panel.add(content);
        return panel;
    }

    // No height clamping: allow vertical growth with divider while padding stays constant
}
