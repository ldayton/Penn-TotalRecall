package components;

import behaviors.singleact.DoneAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/** A <code>JButton</code> hooked up to a {@link behaviors.singleact.DoneAction}. */
@Singleton
public class DoneButton extends JButton {

    private static DoneButton instance;

    /** Creates a new instance, initializing the listeners and appearance. */
    @Inject
    public DoneButton() {
        super(new DoneAction());
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "none");

        // Set the singleton instance after full initialization
        instance = this;
    }

    /**
     * Singleton accessor.
     *
     * @return The singleton <code>DoneButton</code>
     */
    public static DoneButton getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "DoneButton not initialized via DI. Ensure GuiceBootstrap.create() was called"
                            + " first.");
        }
        return instance;
    }
}
