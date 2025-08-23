package components;

import actions.ActionsManager;
import components.shortcuts.ShortcutManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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

    private static components.shortcuts.ShortcutPreferences.ActionConfigListener
            createActionConfigListener(ActionsManager actionsManager) {
        return new components.shortcuts.ShortcutPreferences.ActionConfigListener() {
            @Override
            public void actionConfigUpdated(
                    actions.ActionsFileParser.ActionConfig actionConfig,
                    components.shortcuts.Shortcut oldShortcut) {
                // Update via ActionsManager using the action config
                String id =
                        actionConfig.className()
                                + (actionConfig.arg().orElse(null) != null
                                        ? "-" + actionConfig.arg().orElse(null)
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
