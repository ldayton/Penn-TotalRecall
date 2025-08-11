package components;

import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import behaviors.singleact.DoneAction;

/**
 * A <code>JButton</code> hooked up to a {@link behaviors.singleact.DoneAction}.
 *  
 */
public class DoneButton extends JButton {

	private static DoneButton instance;

	/**
	 * Creates a new instance, initializing the listeners and appearance.
	 */
	private DoneButton() {
		super(new DoneAction());
		getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "none");
	}

	/**
	 * Singleton accessor.
	 * 
	 * @return The singleton <code>DoneButton</code>
	 */
	public static DoneButton getInstance() {
		if (instance == null) {
			instance = new DoneButton();
		}
		return instance;
	}
}
