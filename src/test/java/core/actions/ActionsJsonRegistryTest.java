package core.actions;

import static org.junit.jupiter.api.Assertions.*;

import app.headless.HeadlessTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Test to verify that all actions defined in actions.json are registered in the ActionRegistry. */
class ActionsJsonRegistryTest extends HeadlessTestFixture {

    @Test
    void allJsonActionsAreRegistered() throws Exception {
        // Get the action registry
        ActionRegistry registry = getInstance(ActionRegistry.class);
        assertNotNull(registry, "ActionRegistry should be available");

        // Parse actions.json to get all action class names
        Set<String> jsonActionNames = new HashSet<>();
        try (InputStream is = getClass().getResourceAsStream("/actions.json")) {
            assertNotNull(is, "actions.json should be in resources");

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(is);
            JsonNode actions = root.get("actions");

            assertNotNull(actions, "actions.json should contain 'actions' array");
            assertTrue(actions.isArray(), "'actions' should be an array");

            for (JsonNode action : actions) {
                String className = action.get("class").asText();
                jsonActionNames.add(className);
            }
        }

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
        // Get the action registry
        ActionRegistry registry = getInstance(ActionRegistry.class);

        // Parse actions.json to get all action class names
        Set<String> jsonActionNames = new HashSet<>();
        try (InputStream is = getClass().getResourceAsStream("/actions.json")) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(is);
            JsonNode actions = root.get("actions");

            for (JsonNode action : actions) {
                String className = action.get("class").asText();
                jsonActionNames.add(className);
            }
        }

        // Try to get each action from registry by name
        for (String actionName : jsonActionNames) {
            boolean found = false;
            for (Action action : registry.getAllActions()) {
                if (action.getClass().getSimpleName().equals(actionName)) {
                    found = true;
                    // Verify basic action properties
                    assertNotNull(action.getLabel(), actionName + " should have a non-null label");
                    assertNotNull(
                            action.getTooltip(),
                            actionName + " should have a non-null tooltip (even if empty)");
                    assertNotNull(
                            action.getShortcut(),
                            actionName + " should have a non-null shortcut (even if empty)");
                    break;
                }
            }
            assertTrue(
                    found, "Action " + actionName + " from actions.json should be instantiatable");
        }
    }

    @Test
    void actionCountsMatch() throws Exception {
        // Count actions in JSON
        int jsonActionCount = 0;
        try (InputStream is = getClass().getResourceAsStream("/actions.json")) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(is);
            JsonNode actions = root.get("actions");
            jsonActionCount = actions.size();
        }

        // Count unique action classes in registry
        ActionRegistry registry = getInstance(ActionRegistry.class);
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
