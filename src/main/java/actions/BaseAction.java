package actions;

import core.actions.Action;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all actions in the application.
 *
 * <p>This is a transitional class that bridges the old Swing-based action system with the new
 * UI-agnostic Action interface. It will be removed once all actions are converted.
 *
 * @deprecated Use {@link Action} directly instead
 */
@Deprecated
public abstract class BaseAction extends AbstractAction implements Action {
    private static final Logger logger = LoggerFactory.getLogger(BaseAction.class);

    /**
     * Creates a new BaseAction with the given name.
     *
     * @param name The display name for this action
     */
    protected BaseAction(String name) {
        super(name);
    }

    /**
     * Creates a new BaseAction with the given name and tooltip.
     *
     * @param name The display name for this action
     * @param tooltip The tooltip text for this action
     */
    protected BaseAction(String name, String tooltip) {
        super(name);
        putValue(SHORT_DESCRIPTION, tooltip);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getWhen() == 0) {
            logger.debug("Action performed with zero timestamp: {}", getClass().getSimpleName());
        }

        try {
            performAction(e);
        } catch (Exception ex) {
            logger.error(
                    "Error performing action {}: {}",
                    getClass().getSimpleName(),
                    ex.getMessage(),
                    ex);
        }
    }

    /**
     * Perform the actual action logic. This method should be implemented by subclasses.
     *
     * @param e The action event that triggered this action
     */
    protected abstract void performAction(ActionEvent e);

    // Bridge methods for the new Action interface

    @Override
    public void execute() {
        // Create a dummy ActionEvent for compatibility
        actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Override
    public String getLabel() {
        Object name = getValue(NAME);
        return name != null ? name.toString() : getClass().getSimpleName();
    }

    @Override
    public String getTooltip() {
        Object tooltip = getValue(SHORT_DESCRIPTION);
        return tooltip != null ? tooltip.toString() : "";
    }

    @Override
    public String getShortcut() {
        // Let actions.xml handle shortcuts for now
        return null;
    }
}
