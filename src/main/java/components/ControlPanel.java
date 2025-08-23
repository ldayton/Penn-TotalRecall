package components;

import components.annotations.AnnotationDisplay;
import components.audiofiles.AudioFileDisplay;
import components.wordpool.WordpoolDisplay;
import info.MyColors;
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

/**
 * Custom <code>JPanel</code> that is used as the bottom half of <code>MySplitPane</code>,
 * containing control components such as wordpool display, file list, etc.
 */
@Singleton
public class ControlPanel extends JPanel {

    private static ControlPanel instance;
    private final DoneButton doneButton;

    /** Creates a new instance, initializing listeners and appearance. */
    @Inject
    public ControlPanel(DoneButton doneButton) {
        this.doneButton = doneButton;
        setOpaque(false);

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        add(Box.createHorizontalGlue());
        //		add(Box.createRigidArea(new Dimension(30, 0)));
        //		add(VolumeSliderDisplay.getInstance());
        add(Box.createRigidArea(new Dimension(30, 0)));
        add(AudioFileDisplay.getInstance());
        add(Box.createRigidArea(new Dimension(30, 0)));
        add(WordpoolDisplay.getInstance());
        add(Box.createRigidArea(new Dimension(30, 0)));
        add(AnnotationDisplay.getInstance());
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
                        MyFrame.getInstance().requestFocusInWindow();
                    }
                });

        setBorder(BorderFactory.createEmptyBorder(10, 3, 3, 3));

        // Set the singleton instance after full initialization
        instance = this;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(MyColors.unfocusedColor);
        g.drawLine(0, 0, getWidth() - 1, 0);
    }

    /**
     * Singleton accessor.
     *
     * @return The singleton <code>ControlPanel</code>
     */
    public static ControlPanel getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "ControlPanel not initialized via DI. Ensure GuiceBootstrap.create() was called"
                            + " first.");
        }
        return instance;
    }
}
