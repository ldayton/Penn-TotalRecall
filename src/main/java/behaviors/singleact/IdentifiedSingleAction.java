package behaviors.singleact;

import behaviors.UpdatingAction;

/**
 * An UpdatingAction that associates itself with a name, tool tip, and key binding.
 *
 * <p>All AbstracActions in this program should inherit IdentifiedSingleAction or
 * IdentifiedMultiAction, either directly or indirectly.
 *
 * <p>Inheriting this class allows for the central storage of names, tool tips, and key bindings in
 * the classes Info.ActionNames and Info.KeyBindings.
 */
public abstract class IdentifiedSingleAction extends UpdatingAction {

    public IdentifiedSingleAction() {
        super(null);
    }
}
