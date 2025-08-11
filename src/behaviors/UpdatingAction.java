package behaviors;

import java.awt.event.ActionEvent;
import java.util.ArrayList;

import javax.swing.AbstractAction;

import components.MyMenu;

import control.XActionManager;


/**
 * An AbstractAction that processes updates in program state.
 * 
 * <p>All AbstracActions in this program will indirectly inherit this class when they inherit (directly or indirectly)
 * IdentifiedSingleAction or IdentifiedMultiAction.
 *
 * <p>Inheriting this class forces the writer of an action to decide if there are times when the action should
 * be disabled or have its name changed. For example, StopAction can disable itself if audio is not playing.
 *
 * <p>Note that the update function only affects actions that are bound to action components like buttons.
 * If you manually generate the event by calling <code>actionPerformed(ActionEvent)</code> then you must first verify
 * the action's preconditions are met. The action will succeed whether or not it was enabled.
 *
 */
public abstract class UpdatingAction extends AbstractAction {
	
	public UpdatingAction(Enum<?> e) {
		MyMenu.registerAction(this);
		XActionManager.registerAction(this, e);
	}
	
	private static ArrayList<Long> stamps = new ArrayList<Long>();

	public static ArrayList<Long> getStamps() {
		return stamps;
	}
	
	public void actionPerformed(ActionEvent e)  {
		if(e.getWhen() == 0) {
			System.out.println("zero: " + getClass());
		}
		stamps.add(e.getWhen());
	}
	
	/**
	 * Informs the Action that the program's global state has changed in such a way that the Action may now want to enable/disable itself, or change something else.
	 * This method is called on every IdentifiedAction after many state changes, e.g. audio opening, audio playing, first annotation made, etc.
	 * If your IdentifiedAction requires an update() call at a state change that doesn't currently set updates, add a MyMenu.updateActions() call after that event takes place.
	 * 
	 * <p>Update code must be FAST, since it runs on the event dispatch thread.
	 */
	public abstract void update();
}
