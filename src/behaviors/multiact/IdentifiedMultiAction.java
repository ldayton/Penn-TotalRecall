package behaviors.multiact;

import behaviors.UpdatingAction;


/**
 * An UpdatingAction capable of multiple variations on the same behavior, each associated with a name, tool tip, and key binding.
 * 
 * <p>All <code>AbstracActions</code> in this program should inherit {@link behaviors.singleact.IdentifiedSingleAction} or <code>IdentifiedMultiAction</code>, either directly or indirectly.
 *
 * <p>Inheriting this class allows for the central storage of names, tool tips, and key bindings in the classes
 * {@link control.XActionManager} and @link{info.KeyBindings}. 
 *  
 * <p>Some UpdatingActions need to be instantiated many times (e.g., <code>ZoomAction</code> can zoom in or out), with each resulting object requiring a different name,
 * tool tip, and key binding. This class address that need by requiring an <code>Enum</code> from the action to help identify the correct name/tooltip/keybinding set.
 * See how {@link ZoomAction} instances are handled for an example. 
 *
 * 
 * @see behaviors.singleact.IdentifiedSingleAction
 */
public abstract class IdentifiedMultiAction extends UpdatingAction {

	/**
	 * Creates a SeekAction, using its identifying <code>Enum</code> to associate it with the name, tool tip, and acceleartor key stored in <code>Info.ActionName</code> and <code>Info.KeyBindings</code>.
	 * 
	 * @param e The <code>Enum</code> that will be used in <code>Info.ActionNames</code> and <code>Info.KeyBindings</code> to correctly associate instances with their names, tool tips, and key bindings.
	 */
	public IdentifiedMultiAction(Enum<?> e) {
		super(e);
	}
}
