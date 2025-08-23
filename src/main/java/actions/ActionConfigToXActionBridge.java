package actions;

import actions.ActionsFileParser.ActionConfig;
import java.util.List;
import java.util.stream.Collectors;
import shortcuts.XAction;

/**
 * Bridge class that converts ActionConfig objects to XAction objects. This allows the old
 * ShortcutManager to work with the new ActionsManager system without requiring a complete rewrite
 * of the shortcut management UI.
 */
public class ActionConfigToXActionBridge {

    /**
     * Converts a list of ActionConfig objects to XAction objects.
     *
     * @param actionConfigs The list of ActionConfig objects
     * @return The list of XAction objects
     */
    public static List<XAction> convertToXActions(List<ActionConfig> actionConfigs) {
        return actionConfigs.stream()
                .map(ActionConfigToXActionBridge::convertToXAction)
                .collect(Collectors.toList());
    }

    /**
     * Converts a single ActionConfig to an XAction.
     *
     * @param config The ActionConfig to convert
     * @return The XAction
     */
    public static XAction convertToXAction(ActionConfig config) {
        return new XAction(
                config.className(),
                config.enumValue().orElse(null),
                config.name(),
                config.tooltip().orElse(null),
                config.shortcut().orElse(null));
    }

    /**
     * Converts an XAction back to an ActionConfig.
     *
     * @param xAction The XAction to convert
     * @return The ActionConfig
     */
    public static ActionConfig convertToActionConfig(XAction xAction) {
        return new ActionConfig(
                xAction.className(),
                xAction.name(),
                xAction.tooltip() != null
                        ? java.util.Optional.of(xAction.tooltip())
                        : java.util.Optional.empty(),
                xAction.enumValue() != null
                        ? java.util.Optional.of(xAction.enumValue())
                        : java.util.Optional.empty(),
                xAction.shortcut() != null
                        ? java.util.Optional.of(xAction.shortcut())
                        : java.util.Optional.empty());
    }
}
