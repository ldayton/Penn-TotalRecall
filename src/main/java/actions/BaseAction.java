package actions;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all actions in the application.
 *
 * <p>Provides a clean foundation for actions with proper dependency injection support. This
 * replaces the complex inheritance hierarchy from the behaviors package.
 */
public abstract class BaseAction extends AbstractAction {
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
}
