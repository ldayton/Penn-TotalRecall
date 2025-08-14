package util;

/**
 * Convenience class for storing an AbstractAction's name and tooltip.
 *
 * <p>Instances stored by maps in the info package. Helpful for automatically initializing
 * IdentifiedActions.
 */
public class ActionIdentification {

    private final String actionName;
    private final String toolTip;

    /**
     * Simple constructor passed properties of an AbstractAction.
     *
     * @param actionName Destined to be an Action.NAME
     * @param toolTip Destined to be an Action.SHORT_DESCRIPTION
     */
    public ActionIdentification(String actionName, String toolTip) {
        this.actionName = actionName;
        this.toolTip = toolTip;
    }

    /**
     * Getter for an AbstractAction's name
     *
     * @return An Action.NAME value
     */
    public String getActionName() {
        return actionName;
    }

    /**
     * Getter for an AbstractAction's toolTip
     *
     * @return An Action.SHORT_DESCRIPTION value
     */
    public String getToolTip() {
        return toolTip;
    }
}
