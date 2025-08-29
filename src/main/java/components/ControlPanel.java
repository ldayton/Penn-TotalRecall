package components;

import components.annotations.AnnotationDisplay;
import components.audiofiles.AudioFileDisplay;
import components.wordpool.WordpoolDisplay;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import ui.UiColors;

/**
 * Custom <code>JPanel</code> that is used as the bottom half of <code>ContentSplitPane</code>,
 * containing control components such as wordpool display, file list, etc.
 */
@Singleton
public class ControlPanel extends JPanel {

    /** Creates a new instance, initializing listeners and appearance. */
    @Inject
    public ControlPanel(
            DoneButton doneButton,
            AudioFileDisplay audioFileDisplay,
            AnnotationDisplay annotationDisplay,
            WordpoolDisplay wordpoolDisplay) {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        add(Box.createHorizontalGlue());
        add(Box.createRigidArea(new Dimension(30, 0)));
        add(audioFileDisplay);
        add(Box.createRigidArea(new Dimension(30, 0)));
        add(wordpoolDisplay);
        add(Box.createRigidArea(new Dimension(30, 0)));
        add(annotationDisplay);
        add(Box.createRigidArea(new Dimension(30, 0)));
        add(doneButton);
        add(Box.createRigidArea(new Dimension(30, 0)));
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

        setBorder(BorderFactory.createEmptyBorder(10, 3, 3, 3));
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(UiColors.unfocusedColor);
        g.drawLine(0, 0, getWidth() - 1, 0);
    }
}
