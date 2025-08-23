package components;

import actions.ActionsManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import shortcuts.ShortcutManager;

/**
 * ShortcutFrame that uses the new ActionsManager system instead of the old XActionParser. This
 * provides the same functionality but works with ActionConfig objects from the new system.
 */
@Singleton
public class ShortcutFrame extends ShortcutManager {

    private final ActionsManager actionsManager;

    @Inject
    public ShortcutFrame(ActionsManager actionsManager) {
        super(actionsManager.getAllActionConfigs(), createXActionListener(actionsManager));
        this.actionsManager = actionsManager;
    }

    private static shortcuts.XActionListener createXActionListener(ActionsManager actionsManager) {
        return new shortcuts.XActionListener() {
            @Override
            public void xActionUpdated(shortcuts.XAction xact, shortcuts.Shortcut oldShortcut) {
                // Convert XAction to ActionConfig and update via ActionsManager
                String id = xact.getId();
                // Note: This is a simplified conversion - in a full migration,
                // we'd want to convert XAction to ActionConfig properly
                actionsManager.update(id, oldShortcut);
            }
        };
    }

    public void showShortcutEditor() {
        setLocation(util.GUIUtils.chooseLocation(this));
        setVisible(true);
    }
}
