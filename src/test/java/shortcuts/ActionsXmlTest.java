package shortcuts;

import static org.junit.jupiter.api.Assertions.*;

import env.Platform;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for actions.xml parsing and validation. */
class ActionsXmlTest {

    private final Platform platform = new Platform();

    @Test
    @DisplayName("actions.xml should parse without errors using cross-platform menu modifier")
    void testActionsXmlParsingValid() {
        // Get the actions.xml file from resources
        URL actionsXmlUrl = getClass().getClassLoader().getResource("actions.xml");
        assertNotNull(actionsXmlUrl, "actions.xml should be found in resources");

        // This should parse successfully with cross-platform "menu" modifier
        assertDoesNotThrow(
                () -> {
                    XActionParser parser = new XActionParser(actionsXmlUrl);
                    List<XAction> actions = parser.getXactions();
                    assertNotNull(actions);
                    assertFalse(
                            actions.isEmpty(), "Should parse at least one action from actions.xml");

                    // Verify we have actions with shortcuts that parse correctly
                    boolean hasShortcut =
                            actions.stream().anyMatch(action -> action.shortcut() != null);
                    assertTrue(hasShortcut, "Should have at least one action with a shortcut");
                });
    }
}
