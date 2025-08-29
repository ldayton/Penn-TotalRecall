package ui;

import actions.DoneAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/** A <code>JButton</code> hooked up to a {@link actions.DoneAction}. */
@Singleton
public class DoneButton extends JButton {

    /** Creates a new instance, initializing the listeners and appearance. */
    @Inject
    public DoneButton(DoneAction doneAction) {
        super(doneAction);
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "none");
    }
}
