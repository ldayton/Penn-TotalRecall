package actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.*;

import actions.ActionsFileParser.ActionConfig;
import behaviors.UpdatingAction;
import env.KeyboardManager;
import env.Platform;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shortcuts.Shortcut;

@ExtendWith(MockitoExtension.class)
class ActionsManagerTest {

    @Mock private ActionsFileParser actionsFileParser;
    @Mock private Platform platform;
    @Mock private KeyboardManager keyboardManager;
    private TestableUpdatingAction testAction;

    private ActionsManager actionsManager;

    @BeforeEach
    void setUp() {
        actionsManager = new ActionsManager(actionsFileParser);
        testAction = new TestableUpdatingAction();
    }

    @Test
    @DisplayName("ActionsManager should initialize and load action configurations")
    void shouldInitializeAndLoadActionConfigurations() throws Exception {
        // Given
        var actionConfigs = List.of(
            new ActionConfig("behaviors.singleact.DoneAction", "Mark Complete", 
                           Optional.of("Mark current item as complete"), 
                           Optional.empty(), Optional.empty()),
            new ActionConfig("behaviors.singleact.PlayPauseAction", "Play/Pause", 
                           Optional.empty(), Optional.empty(), Optional.empty())
        );
        
        doReturn(actionConfigs).when(actionsFileParser).parseActionsFromClasspath("/actions.xml");

        // When
        actionsManager.initialize();

        // Then
        verify(actionsFileParser).parseActionsFromClasspath("/actions.xml");
        assertEquals(2, actionsManager.getAllActionConfigs().size());
        
        // Verify action configs are stored by ID
        var doneAction = actionsManager.getActionConfig("behaviors.singleact.DoneAction");
        assertTrue(doneAction.isPresent());
        assertEquals("Mark Complete", doneAction.get().name());
    }

    @Test
    @DisplayName("ActionsManager should handle initialization errors")
    void shouldHandleInitializationErrors() throws Exception {
        // Given
        doThrow(new ActionsFileParser.ActionParseException("Test error"))
            .when(actionsFileParser).parseActionsFromClasspath("/actions.xml");

        // When & Then
        assertThrows(RuntimeException.class, () -> actionsManager.initialize());
    }

    @Test
    @DisplayName("ActionsManager should register and update actions")
    void shouldRegisterAndUpdateActions() throws Exception {
        // Given
        var actionConfigs = List.of(
            new ActionConfig("actions.ActionsManagerTest$TestableUpdatingAction", "Mark Complete", 
                           Optional.of("Mark current item as complete"), 
                           Optional.empty(), Optional.empty())
        );
        
        doReturn(actionConfigs).when(actionsFileParser).parseActionsFromClasspath("/actions.xml");
        
        actionsManager.initialize();

        // When
        actionsManager.registerAction(testAction, null);

        // Then
        assertEquals("Mark Complete", testAction.getValue(Action.NAME));
        assertEquals("Mark current item as complete", testAction.getValue(Action.SHORT_DESCRIPTION));
        assertNull(testAction.getValue(Action.ACCELERATOR_KEY));
    }

    @Test
    @DisplayName("ActionsManager should handle actions with shortcuts")
    void shouldHandleActionsWithShortcuts() throws Exception {
        // Given
        var keyStroke = KeyStroke.getKeyStroke("ctrl S");
        var keyboardManager = mock(KeyboardManager.class);
        var shortcut = new Shortcut(keyStroke, keyboardManager);
        
        var actionConfigs = List.of(
            new ActionConfig("actions.ActionsManagerTest$TestableUpdatingAction", "Save", 
                           Optional.empty(), Optional.empty(), Optional.of(shortcut))
        );
        
        doReturn(actionConfigs).when(actionsFileParser).parseActionsFromClasspath("/actions.xml");
        
        actionsManager.initialize();

        // When
        actionsManager.registerAction(testAction, null);

        // Then
        assertEquals("Save", testAction.getValue(Action.NAME));
        assertEquals(keyStroke, testAction.getValue(Action.ACCELERATOR_KEY));
    }

    @Test
    @DisplayName("ActionsManager should handle actions with enum values")
    void shouldHandleActionsWithEnumValues() throws Exception {
        // Given - Action config should use the class name + enum value combination that will be looked up
        var actionConfigs = List.of(
            new ActionConfig("actions.ActionsManagerTest$TestableUpdatingAction", "Add Audio Files...", 
                           Optional.of("Select File or Folder"), 
                           Optional.of("TEST_VALUE"), Optional.empty())
        );
        
        doReturn(actionConfigs).when(actionsFileParser).parseActionsFromClasspath("/actions.xml");
        
        actionsManager.initialize();

        // When - Register with enum value that matches the action config
        actionsManager.registerAction(testAction, TestEnum.TEST_VALUE);

        // Then
        assertEquals("Add Audio Files...", testAction.getValue(Action.NAME));
        assertEquals("Select File or Folder", testAction.getValue(Action.SHORT_DESCRIPTION));
    }

    @Test
    @DisplayName("ActionsManager should lookup KeyStrokes correctly")
    void shouldLookupKeyStrokesCorrectly() throws Exception {
        // Given
        var keyStroke = KeyStroke.getKeyStroke("ctrl S");
        var keyboardManager = mock(KeyboardManager.class);
        var shortcut = new Shortcut(keyStroke, keyboardManager);
        
        var actionConfigs = List.of(
            new ActionConfig("actions.ActionsManagerTest$TestableUpdatingAction", "Save", 
                           Optional.empty(), Optional.empty(), Optional.of(shortcut))
        );
        
        doReturn(actionConfigs).when(actionsFileParser).parseActionsFromClasspath("/actions.xml");
        
        actionsManager.initialize();

        // When
        var result = actionsManager.lookup(testAction, null);

        // Then
        assertEquals(keyStroke, result);
    }

    @Test
    @DisplayName("ActionsManager should return null for non-existent actions")
    void shouldReturnNullForNonExistentActions() throws Exception {
        // Given
        doReturn(List.of()).when(actionsFileParser).parseActionsFromClasspath("/actions.xml");
        
        actionsManager.initialize();

        // When
        var result = actionsManager.lookup(testAction, null);

        // Then
        assertNull(result);
    }

    // Test enum for testing
    private enum TestEnum {
        TEST_VALUE
    }

    // Test double for UpdatingAction that doesn't call super constructor
    private static class TestableUpdatingAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            // No-op for testing
        }
    }
}
