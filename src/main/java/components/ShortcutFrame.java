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
        super(actionsManager.getAllActionConfigs(), createActionConfigListener(actionsManager));
        this.actionsManager = actionsManager;
    }

    private static shortcuts.ShortcutPreferences.ActionConfigListener createActionConfigListener(
            ActionsManager actionsManager) {
        return new shortcuts.ShortcutPreferences.ActionConfigListener() {
            @Override
            public void actionConfigUpdated(
                    actions.ActionsFileParser.ActionConfig actionConfig,
                    shortcuts.Shortcut oldShortcut) {
                // Update via ActionsManager using the action config
                String id =
                        actionConfig.className()
                                + (actionConfig.enumValue().orElse(null) != null
                                        ? "-" + actionConfig.enumValue().orElse(null)
                                        : "");
                actionsManager.update(id, oldShortcut);
            }
        };
    }

    public void showShortcutEditor() {
        setLocation(util.GUIUtils.chooseLocation(this));
        setVisible(true);
    }
}
