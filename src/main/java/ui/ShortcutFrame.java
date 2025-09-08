package ui;

import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.EditShortcutsRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import ui.actions.ActionsManager;
import ui.shortcuts.ShortcutManager;

/**
 * ShortcutFrame that uses the new ActionsManager system instead of the old XActionParser. This
 * provides the same functionality but works with ActionConfig objects from the new system.
 */
@Singleton
public class ShortcutFrame extends ShortcutManager {

    @Inject
    public ShortcutFrame(ActionsManager actionsManager, EventDispatchBus eventBus) {
        super(actionsManager.getAllActionConfigs(), createActionConfigListener(actionsManager));
        eventBus.subscribe(this);
    }

    private static ui.shortcuts.ShortcutPreferences.ActionConfigListener createActionConfigListener(
            ActionsManager actionsManager) {
        return new ui.shortcuts.ShortcutPreferences.ActionConfigListener() {
            @Override
            public void actionConfigUpdated(
                    ui.actions.ActionsFileParser.ActionConfig actionConfig,
                    ui.shortcuts.Shortcut oldShortcut) {
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
        setLocation(ui.DialogCentering.chooseLocation(this));
        setVisible(true);
    }

    @Subscribe
    public void handleEditShortcutsRequested(EditShortcutsRequestedEvent event) {
        showShortcutEditor();
    }
}
