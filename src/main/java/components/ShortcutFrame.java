package components;

import actions.ActionsManagerBridge;
import shortcuts.ShortcutManager;

/**
 * ShortcutFrame that uses the new ActionsManager system instead of the old XActionParser.
 * This provides the same functionality but works with ActionConfig objects from the new system.
 */
public class ShortcutFrame extends ShortcutManager {

    private ShortcutFrame(java.util.List<actions.ActionsFileParser.ActionConfig> actionConfigs, shortcuts.XActionListener listener) {
        super(actionConfigs, listener);
    }

    public static ShortcutFrame createDefault() {
        // Get action configs from the new ActionsManager system
        var actionConfigs = ActionsManagerBridge.getAllActionConfigs();
        return new ShortcutFrame(actionConfigs, ActionsManagerBridge.listener);
    }

    public static void showShortcutEditor() {
        var frame = createDefault();
        frame.setLocation(util.GUIUtils.chooseLocation(frame));
        frame.setVisible(true);
    }
}
