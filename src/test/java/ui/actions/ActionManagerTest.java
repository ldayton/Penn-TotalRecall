package ui.actions;

import static org.junit.jupiter.api.Assertions.*;

import app.headless.HeadlessTestFixture;
import core.actions.ActionRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import ui.KeyboardManager;
import ui.adapters.ShortcutConverter;
import ui.adapters.SwingActionConfig;

/**
 * Test that ActionManager can properly load and convert action configurations from JSON. This test
 * catches issues like unrecognized key names (e.g., "minus") that cause conversion failures.
 */
class ActionManagerTest extends HeadlessTestFixture {

    @Test
    void canLoadAllActionConfigsFromJson() {
        // Get dependencies from DI
        ActionRegistry actionRegistry = getInstance(ActionRegistry.class);
        KeyboardManager keyboardManager = getInstance(KeyboardManager.class);

        // Create ActionManager with dependencies
        ShortcutConverter converter = new ShortcutConverter(keyboardManager);
        ActionManager actionManager = new ActionManager(actionRegistry, converter);

        // Initialize (loads actions.json)
        actionManager.initialize();

        // Get all configs - this triggers ShortcutSpec to KeyStroke conversion
        // If any key names like "minus" aren't recognized, this will throw
        List<SwingActionConfig> configs =
                assertDoesNotThrow(
                        () -> actionManager.getAllActionConfigs(),
                        "Should be able to convert all action configs from JSON without errors");

        assertFalse(configs.isEmpty(), "Should have loaded action configs");

        // Verify each config converted successfully
        for (SwingActionConfig config : configs) {
            assertNotNull(config.className());
            if (config.shortcut().isPresent()) {
                assertNotNull(
                        config.shortcut().get().stroke,
                        config.className() + " shortcut should have converted to KeyStroke");
            }
        }

        System.out.println(
                "Successfully loaded and converted " + configs.size() + " action configs");
    }
}
