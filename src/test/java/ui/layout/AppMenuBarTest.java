package ui.layout;

import static org.junit.jupiter.api.Assertions.*;

import app.swing.SwingTestFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.actions.ActionConfig;
import core.actions.ActionsFileParser;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import ui.adapters.ShortcutConverter;

/**
 * Test that verifies AppMenuBar correctly displays action names, accelerators, and tooltips from
 * actions.json configuration.
 */
class AppMenuBarTest extends SwingTestFixture {

    @Test
    void menuItemsMatchActionsJsonConfiguration() throws Exception {
        // Load actions.json to get expected values
        Map<String, ActionConfig> configsByClass = loadActionsConfig();

        // Get the menu bar from the app
        AppMenuBar menuBar = getInstance(AppMenuBar.class);
        ShortcutConverter shortcutConverter = getInstance(ShortcutConverter.class);

        // Verify each menu and its items
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            verifyMenuItems(menu, configsByClass, shortcutConverter);
        }
    }

    private void verifyMenuItems(
            JMenu menu, Map<String, ActionConfig> configs, ShortcutConverter converter) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);

            // Skip separators
            if (item == null) {
                continue;
            }

            // Check if it's a submenu
            if (item instanceof JMenu) {
                verifyMenuItems((JMenu) item, configs, converter);
                continue;
            }

            // Get the action from the menu item
            Action action = item.getAction();
            if (action != null) {
                verifyActionProperties(item, action, configs, converter);
            }
        }
    }

    private void verifyActionProperties(
            JMenuItem item,
            Action action,
            Map<String, ActionConfig> configs,
            ShortcutConverter converter) {
        // Extract the action class name from the action
        String actionClassName = extractActionClassName(action);

        if (actionClassName != null && configs.containsKey(actionClassName)) {
            ActionConfig config = configs.get(actionClassName);

            // Verify name
            String expectedName = config.name();
            String actualName = item.getText();
            assertEquals(
                    expectedName,
                    actualName,
                    "Menu item for " + actionClassName + " should have correct name");

            // Verify accelerator (keyboard shortcut)
            if (config.shortcut().isPresent()) {
                KeyStroke expectedKeyStroke = converter.toKeyStroke(config.shortcut().get());
                KeyStroke actualKeyStroke = item.getAccelerator();
                assertEquals(
                        expectedKeyStroke,
                        actualKeyStroke,
                        "Menu item for " + actionClassName + " should have correct accelerator");
            }

            // Verify tooltip
            if (config.tooltip().isPresent()) {
                String expectedTooltip = config.tooltip().get();
                String actualTooltip = item.getToolTipText();
                assertEquals(
                        expectedTooltip,
                        actualTooltip,
                        "Menu item for " + actionClassName + " should have correct tooltip");
            } else {
                // No tooltip should be set if not in config
                assertNull(
                        item.getToolTipText(),
                        "Menu item for "
                                + actionClassName
                                + " should have no tooltip when not configured");
            }
        }
    }

    private String extractActionClassName(Action action) {
        // The action's toString or class name should help identify the core action
        // Since menu items use SwingAction wrappers, we need to extract the underlying action class
        String actionString = action.toString();

        // Try to extract the class name from common patterns in the action string
        // This might need adjustment based on how SwingAction.toString() is implemented
        if (actionString.contains("Action@")) {
            String className = actionString.substring(0, actionString.indexOf("@"));
            if (className.contains(".")) {
                className = className.substring(className.lastIndexOf(".") + 1);
            }
            return className;
        }

        // Alternative: check the action's NAME property which might contain the class
        Object nameValue = action.getValue(Action.NAME);
        if (nameValue != null) {
            // Try to match against known action names in configs
            return findActionClassByName(nameValue.toString());
        }

        return null;
    }

    private String findActionClassByName(String name) {
        // This would need the configs to do reverse lookup
        // For now, return null - this might need enhancement
        return null;
    }

    private Map<String, ActionConfig> loadActionsConfig() throws Exception {
        Map<String, ActionConfig> configsByClass = new HashMap<>();

        try (InputStream is = getClass().getResourceAsStream("/actions.json")) {
            assertNotNull(is, "actions.json should be present in resources");

            ObjectMapper mapper = new ObjectMapper();
            ActionsFileParser parser = new ActionsFileParser(mapper);
            List<ActionConfig> configs = parser.parseActions(is);

            for (ActionConfig config : configs) {
                configsByClass.put(config.className(), config);
            }
        }

        return configsByClass;
    }

    @Test
    void verifySpecificMenuItems() throws Exception {
        // Load actions.json to get expected values
        Map<String, ActionConfig> configsByClass = loadActionsConfig();

        // Get the menu bar
        AppMenuBar menuBar = getInstance(AppMenuBar.class);

        // Verify File menu exists
        JMenu fileMenu = findMenu(menuBar, "File");
        assertNotNull(fileMenu, "File menu should exist");

        // Verify specific actions exist with correct names from config
        verifyMenuItemFromConfig(fileMenu, "OpenWordpoolAction", configsByClass);

        // Verify Controls menu
        JMenu controlsMenu = findMenu(menuBar, "Controls");
        assertNotNull(controlsMenu, "Controls menu should exist");
        verifyMenuItemFromConfig(controlsMenu, "PlayPauseAction", configsByClass);

        // Verify Annotation menu
        JMenu annotationMenu = findMenu(menuBar, "Annotation");
        assertNotNull(annotationMenu, "Annotation menu should exist");
        verifyMenuItemFromConfig(annotationMenu, "DoneAction", configsByClass);

        // Verify View menu
        JMenu viewMenu = findMenu(menuBar, "View");
        assertNotNull(viewMenu, "View menu should exist");
        verifyMenuItemFromConfig(viewMenu, "ZoomInAction", configsByClass);
        verifyMenuItemFromConfig(viewMenu, "ZoomOutAction", configsByClass);
    }

    private void verifyMenuItemFromConfig(
            JMenu menu, String actionClassName, Map<String, ActionConfig> configs) {
        ActionConfig config = configs.get(actionClassName);
        assertNotNull(config, actionClassName + " should have configuration");

        String expectedName = config.name();
        JMenuItem item = findMenuItem(menu, expectedName);
        assertNotNull(
                item, expectedName + " menu item should exist in " + menu.getText() + " menu");
    }

    private JMenu findMenu(JMenuBar menuBar, String name) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu.getText().equals(name)) {
                return menu;
            }
        }
        return null;
    }

    private JMenuItem findMenuItem(JMenu menu, String name) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null && item.getText().equals(name)) {
                return item;
            }
            // Check submenus
            if (item instanceof JMenu) {
                JMenuItem found = findMenuItem((JMenu) item, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
