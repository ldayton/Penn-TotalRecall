package core.actions;

import static org.junit.jupiter.api.Assertions.*;

import app.headless.HeadlessTestFixture;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Test to verify that all actions defined in actions.json are registered in the ActionRegistry. */
class ActionsJsonRegistryTest extends HeadlessTestFixture {

    @Test
    void allJsonActionsAreRegistered() throws Exception {
        // Get the action registry and initialize it to load actions.json
        ActionRegistry registry = getInstance(ActionRegistry.class);
        assertNotNull(registry, "ActionRegistry should be available");

        // Initialize the registry - this loads actions.json
        registry.initialize();

        // Get all configured action names from the loaded JSON
        Set<String> jsonActionNames = new HashSet<>();
        for (ActionConfig config : registry.getAllConfigs()) {
            jsonActionNames.add(config.className());
        }

        assertFalse(jsonActionNames.isEmpty(), "Should have loaded action configs from JSON");

        // Get all registered action class names
        Set<String> registeredActionNames = new HashSet<>();
        for (Action action : registry.getAllActions()) {
            String simpleName = action.getClass().getSimpleName();
            registeredActionNames.add(simpleName);
        }

        // Verify each JSON action is registered
        Set<String> missingActions = new HashSet<>();
        for (String jsonAction : jsonActionNames) {
            if (!registeredActionNames.contains(jsonAction)) {
                missingActions.add(jsonAction);
            }
        }

        assertTrue(
                missingActions.isEmpty(),
                "The following actions from actions.json are not registered: " + missingActions);

        // Also report if there are registered actions not in JSON (informational)
        Set<String> extraActions = new HashSet<>(registeredActionNames);
        extraActions.removeAll(jsonActionNames);
        if (!extraActions.isEmpty()) {
            System.out.println("Actions registered but not in actions.json: " + extraActions);
        }
    }

    @Test
    void allJsonActionsCanBeInstantiated() throws Exception {
        // Get the action registry and initialize it
        ActionRegistry registry = getInstance(ActionRegistry.class);
        registry.initialize();

        // Get action configs from the loaded JSON
        for (ActionConfig config : registry.getAllConfigs()) {
            String actionName = config.className();

            // Try to get the action from registry
            boolean found = false;
            for (Action action : registry.getAllActions()) {
                if (action.getClass().getSimpleName().equals(actionName)) {
                    found = true;
                    // Verify basic action properties
                    assertNotNull(action.getLabel(), actionName + " should have a non-null label");
                    assertNotNull(
                            action.getTooltip(),
                            actionName
                                    + " should have a non-null tooltip (even if Optional.empty())");
                    assertNotNull(
                            action.getShortcut(),
                            actionName
                                    + " should have a non-null shortcut (even if"
                                    + " Optional.empty())");

                    // Verify the config itself is valid
                    assertNotNull(config.name(), actionName + " config should have a name");
                    assertNotNull(
                            config.className(), actionName + " config should have a className");
                    break;
                }
            }
            assertTrue(
                    found, "Action " + actionName + " from actions.json should be instantiatable");
        }
    }

    @Test
    void actionCountsMatch() throws Exception {
        // Get the action registry and initialize it
        ActionRegistry registry = getInstance(ActionRegistry.class);
        registry.initialize();

        // Count actions in loaded configs
        int jsonActionCount = registry.getAllConfigs().size();

        // Count unique action classes in registry
        Set<String> uniqueActionClasses = new HashSet<>();
        for (Action action : registry.getAllActions()) {
            uniqueActionClasses.add(action.getClass().getSimpleName());
        }

        System.out.println("Actions in JSON: " + jsonActionCount);
        System.out.println("Unique action classes registered: " + uniqueActionClasses.size());

        // They might not match exactly if some actions are registered multiple times
        // or if there are actions not in JSON, but we should have at least as many
        // registered as in JSON
        assertTrue(
                uniqueActionClasses.size() >= jsonActionCount,
                String.format(
                        "Registry has %d unique actions but JSON has %d",
                        uniqueActionClasses.size(), jsonActionCount));
    }
}
