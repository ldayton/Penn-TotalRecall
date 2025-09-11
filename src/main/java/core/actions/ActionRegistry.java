package core.actions;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ActionRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ActionRegistry.class);

    private final Map<Class<? extends Action>, Action> actionsByClass = new HashMap<>();
    private final Map<String, Action> actionsByName = new HashMap<>();
    private final Map<String, ActionConfig> configs = new HashMap<>();
    private final Set<Action> allActions;
    private final ActionsFileParser parser;
    private boolean initialized = false;

    @Inject
    public ActionRegistry(Set<Action> actions, @NonNull ActionsFileParser parser) {
        this.allActions = actions;
        this.parser = parser;

        for (Action action : actions) {
            actionsByClass.put(action.getClass(), action);
            actionsByName.put(action.getClass().getSimpleName(), action);
        }
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        try (InputStream is = getClass().getResourceAsStream("/actions.json")) {
            if (is == null) {
                logger.error("actions.json not found in resources");
                return;
            }

            List<ActionConfig> actionConfigs = parser.parseActions(is);
            for (ActionConfig config : actionConfigs) {
                configs.put(config.className(), config);
            }

            logger.info("Loaded {} action configurations", configs.size());
            initialized = true;
        } catch (Exception e) {
            logger.error("Failed to initialize ActionRegistry", e);
            throw new RuntimeException("Failed to initialize ActionRegistry", e);
        }
    }

    public Set<Action> getAllActions() {
        return allActions;
    }

    public <T extends Action> Optional<T> getAction(Class<T> actionClass) {
        @SuppressWarnings("unchecked")
        T action = (T) actionsByClass.get(actionClass);
        return Optional.ofNullable(action);
    }

    public Optional<Action> getAction(String className) {
        return Optional.ofNullable(actionsByName.get(className));
    }

    public Optional<ActionConfig> getConfig(String className) {
        return Optional.ofNullable(configs.get(className));
    }

    public List<ActionConfig> getAllConfigs() {
        return List.copyOf(configs.values());
    }
}
