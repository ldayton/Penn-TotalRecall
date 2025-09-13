package ui.actions;

import core.actions.ActionRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import lombok.NonNull;
import ui.adapters.ShortcutConverter;
import ui.adapters.SwingActionConfig;
import ui.adapters.SwingActionRegistry;
import ui.shortcuts.Shortcut;

@Singleton
public class ActionManager {

    private final ActionRegistry actionRegistry;
    private final ShortcutConverter converter;
    private final SwingActionRegistry swingActions;
    private final Map<String, Set<Action>> listenersMap = new HashMap<>();
    private final Map<String, List<InputMapPair>> inputMapMap = new HashMap<>();

    @Inject
    public ActionManager(
            @NonNull ActionRegistry actionRegistry,
            @NonNull ShortcutConverter converter,
            @NonNull SwingActionRegistry swingActions) {
        this.actionRegistry = actionRegistry;
        this.converter = converter;
        this.swingActions = swingActions;
    }

    public void initialize() {
        actionRegistry.initialize();

        // Register all SwingActions from the registry
        for (var swingAction : swingActions.getAllSwingActions()) {
            var coreAction = swingAction.getCoreAction();
            String className = coreAction.getClass().getSimpleName();
            listenersMap.computeIfAbsent(className, _ -> new HashSet<>()).add(swingAction);
        }
    }

    public KeyStroke lookup(Action action, Enum<?> e) {
        String className = action.getClass().getSimpleName();
        return lookup(className);
    }

    public KeyStroke lookup(String className) {
        var config = actionRegistry.getConfig(className);
        if (config.isPresent() && config.get().shortcut().isPresent()) {
            var spec = config.get().shortcut().get();
            return converter.toKeyStroke(spec);
        }
        return null;
    }

    public void registerInputMap(Action action, Enum<?> e, String mapKey, InputMap map) {
        String className = action.getClass().getSimpleName();
        inputMapMap
                .computeIfAbsent(className, _ -> new ArrayList<>())
                .add(new InputMapPair(mapKey, map));
    }

    public void registerAction(Action action, Enum<?> e) {
        String className = action.getClass().getSimpleName();
        listenersMap.computeIfAbsent(className, _ -> new HashSet<>()).add(action);
        update(className, null);
    }

    public void registerCoreAction(core.actions.Action action) {
        var swingAction = swingActions.get(action.getClass());
        String className = action.getClass().getSimpleName();
        listenersMap.computeIfAbsent(className, _ -> new HashSet<>()).add(swingAction);
        update(className, null);
    }

    public void update(String className, Shortcut oldShortcut) {
        var actions = listenersMap.get(className);
        var config = actionRegistry.getConfig(className);

        if (config.isPresent() && actions != null) {
            var actionConfig = config.get();
            KeyStroke stroke = actionConfig.shortcut().map(converter::toKeyStroke).orElse(null);

            for (var action : actions) {
                action.putValue(Action.NAME, actionConfig.name());
                action.putValue(Action.SHORT_DESCRIPTION, actionConfig.tooltip().orElse(null));
                action.putValue(Action.ACCELERATOR_KEY, stroke);
            }

            var inputMapPairs = inputMapMap.get(className);
            if (inputMapPairs != null) {
                for (var pair : inputMapPairs) {
                    var inputMap = pair.inputMap();
                    var oldStroke = oldShortcut != null ? oldShortcut.stroke : null;
                    var inputMapKey = pair.mapKey();

                    if (oldStroke != null) {
                        inputMap.remove(oldStroke);
                    }
                    if (stroke != null) {
                        inputMap.put(stroke, inputMapKey);
                    }
                }
            }
        }
    }

    public Optional<SwingActionConfig> getActionConfig(String className) {
        return actionRegistry.getConfig(className).map(converter::toSwingConfig);
    }

    public List<SwingActionConfig> getAllActionConfigs() {
        return actionRegistry.getAllConfigs().stream().map(converter::toSwingConfig).toList();
    }

    private record InputMapPair(String mapKey, InputMap inputMap) {}
}
