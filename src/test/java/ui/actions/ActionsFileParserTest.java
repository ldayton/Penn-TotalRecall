package ui.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import core.env.Platform;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ui.KeyboardManager;
import ui.actions.ActionsFileParser.ActionConfig;

@ExtendWith(MockitoExtension.class)
class ActionsFileParserTest {

    @Mock private Platform platform;

    @Mock private KeyboardManager keyboardManager;

    private ActionsFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new ActionsFileParser(platform, keyboardManager);
        // Reset mocks to ensure clean state between tests
        org.mockito.Mockito.reset(platform, keyboardManager);
    }

    @Test
    void shouldParseBasicActionWithoutOptionalFields() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction" name="Mark Complete" />
                </actions>
                """;

        // When
        List<ActionConfig> actions = parseFromString(xml);

        // Then
        assertEquals(1, actions.size());
        ActionConfig action = actions.get(0);
        assertEquals("actions.DoneAction", action.className());
        assertEquals("Mark Complete", action.name());
        assertTrue(action.tooltip().isEmpty());
        assertTrue(action.arg().isEmpty());
    }

    @Test
    void shouldParseActionWithAllFields() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.OpenAudioLocationAction"
                            name="Add Audio Files..."
                            tooltip="Select File or Folder"
                            arg="SelectionMode.FILES_AND_DIRECTORIES"
                            os="Linux,Windows" />
                </actions>
                """;

        // When - mock Linux platform to match OS restriction
        when(platform.detect()).thenReturn(Platform.PlatformType.LINUX);
        List<ActionConfig> actions = parseFromString(xml);

        // Then
        assertEquals(1, actions.size());
        ActionConfig action = actions.get(0);
        assertEquals("actions.OpenAudioLocationAction", action.className());
        assertEquals("Add Audio Files...", action.name());
        assertEquals("Select File or Folder", action.tooltip().orElse(null));
        assertEquals("SelectionMode.FILES_AND_DIRECTORIES", action.arg().orElse(null));
    }

    @Test
    void shouldParseMultipleActions() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction" name="Mark Complete" />
                    <action class="actions.PlayPauseAction" name="Play/Pause" />
                    <action class="actions.StopAction" name="Go to Start" />
                </actions>
                """;

        // When
        List<ActionConfig> actions = parseFromString(xml);

        // Then
        assertEquals(3, actions.size());
        assertEquals("actions.DoneAction", actions.get(0).className());
        assertEquals("actions.PlayPauseAction", actions.get(1).className());
        assertEquals("actions.StopAction", actions.get(2).className());
    }

    @Test
    void shouldFilterActionsByPlatform() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction" name="Mark Complete" />
                    <action class="actions.OpenAudioLocationAction"
                            name="Add Audio Files..."
                            os="Linux,Windows" />
                    <action class="core.actions.AboutAction"
                            name="About"
                            os="Windows,Linux" />
                </actions>
                """;

        // When - macOS platform
        when(platform.detect()).thenReturn(Platform.PlatformType.MACOS);
        List<ActionConfig> actions = parseFromString(xml);

        // Then - only the action without OS restriction should be included
        assertEquals(1, actions.size());
        assertEquals("actions.DoneAction", actions.get(0).className());
    }

    @Test
    void shouldIncludeActionsForMatchingPlatform() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.OpenAudioLocationAction"
                            name="Add Audio Files..."
                            os="Linux,Windows" />
                </actions>
                """;

        // When - Linux platform
        when(platform.detect()).thenReturn(Platform.PlatformType.LINUX);
        List<ActionConfig> actions = parseFromString(xml);

        // Then
        assertEquals(1, actions.size());
        assertEquals("actions.OpenAudioLocationAction", actions.get(0).className());
    }

    @Test
    void shouldHandleEmptyActionsElement() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                </actions>
                """;

        // When
        List<ActionConfig> actions = parseFromString(xml);

        // Then
        assertTrue(actions.isEmpty());
    }

    @Test
    void shouldHandleCommentsInXml() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <!-- This is a comment -->
                    <action class="actions.DoneAction" name="Mark Complete" />
                    <!-- Another comment -->
                </actions>
                """;

        // When
        List<ActionConfig> actions = parseFromString(xml);

        // Then
        assertEquals(1, actions.size());
        assertEquals("actions.DoneAction", actions.get(0).className());
    }

    @Test
    void shouldThrowExceptionForUnknownOsValues() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction"
                            name="Mark Complete"
                            os="UnknownOS,Linux" />
                </actions>
                """;

        // When & Then - should fail fast for unknown OS names
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    @Test
    void shouldParseFromClasspath() throws Exception {
        // Given - set up lenient mocks to handle any key name
        when(keyboardManager.xmlKeynameToInternalForm(anyString()))
                .thenAnswer(
                        invocation -> {
                            String keyName = invocation.getArgument(0).toString();
                            // Map common mask names to their internal forms
                            return switch (keyName) {
                                case "menu" -> "ctrl";
                                case "shift" -> "shift";
                                case "alt" -> "alt";
                                default -> keyName; // For letter keys, keep as-is (uppercase)
                            };
                        });

        // When
        List<ActionConfig> actions = parser.parseActionsFromClasspath("/actions.xml");

        // Then
        assertFalse(actions.isEmpty());
        // Verify we get some expected actions
        boolean hasDoneAction =
                actions.stream()
                        .anyMatch(action -> "actions.DoneAction".equals(action.className()));
        assertTrue(hasDoneAction);
    }

    @Test
    void shouldThrowExceptionForMissingClasspathResource() {
        // When & Then
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parser.parseActionsFromClasspath("/nonexistent.xml");
                });
    }

    @Test
    void shouldThrowExceptionForShortcutWithNoKeys() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction" name="Mark Complete">
                        <shortcut>
                            <mask keyname="menu" />
                            <mask keyname="shift" />
                        </shortcut>
                    </action>
                </actions>
                """;

        // When & Then - should fail fast for shortcut with no keys
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    @Test
    void shouldThrowExceptionForUnknownXmlAttribute() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction"
                            name="Mark Complete"
                            unknownAttribute="value" />
                </actions>
                """;

        // When & Then - should fail fast for unknown attributes
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    @Test
    void shouldThrowExceptionForMissingClassName() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action name="Mark Complete" />
                </actions>
                """;

        // When & Then - should fail fast, not return empty list
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    @Test
    void shouldThrowExceptionForEmptyClassName() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="" name="Mark Complete" />
                </actions>
                """;

        // When & Then - should fail fast, not return empty list
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    @Test
    void shouldThrowExceptionForMissingName() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction" />
                </actions>
                """;

        // When & Then - should fail fast, not return empty list
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    @Test
    void shouldThrowExceptionForEmptyName() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction" name="" />
                </actions>
                """;

        // When & Then - should fail fast, not return empty list
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    @Test
    void shouldHandleWhitespaceInOsAttribute() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction"
                            name="Mark Complete"
                            os=" Linux , Windows " />
                </actions>
                """;

        // When - Windows platform
        when(platform.detect()).thenReturn(Platform.PlatformType.WINDOWS);
        List<ActionConfig> actions = parseFromString(xml);

        // Then
        assertEquals(1, actions.size());
        assertEquals("actions.DoneAction", actions.get(0).className());
    }

    @Test
    void shouldHandleSingleOsValue() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction"
                            name="Mark Complete"
                            os="Mac OS X" />
                </actions>
                """;

        // When - macOS platform
        when(platform.detect()).thenReturn(Platform.PlatformType.MACOS);
        List<ActionConfig> actions = parseFromString(xml);

        // Then
        assertEquals(1, actions.size());
        assertEquals("actions.DoneAction", actions.get(0).className());
    }

    @Test
    void shouldHandleNullOsAttribute() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction"
                            name="Mark Complete" />
                </actions>
                """;

        // When
        List<ActionConfig> actions = parseFromString(xml);

        // Then - should be included regardless of platform
        assertEquals(1, actions.size());
        assertEquals("actions.DoneAction", actions.get(0).className());
    }

    @Test
    void shouldHandleEmptyOptionalFields() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction"
                            name="Mark Complete"
                            tooltip=""
                            enum="" />
                </actions>
                """;

        // When
        List<ActionConfig> actions = parseFromString(xml);

        // Then
        assertEquals(1, actions.size());
        ActionConfig action = actions.get(0);
        assertTrue(action.tooltip().isEmpty());
        assertTrue(action.arg().isEmpty());
    }

    @Test
    void shouldHandleWhitespaceInOptionalFields() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.OpenAudioLocationAction"
                            name="Add Audio Files..."
                            tooltip=" Select File or Folder "
                            arg=" SelectionMode.FILES_AND_DIRECTORIES " />
                </actions>
                """;

        // When
        List<ActionConfig> actions = parseFromString(xml);

        // Then
        assertEquals(1, actions.size());
        ActionConfig action = actions.get(0);
        assertEquals("Select File or Folder", action.tooltip().orElse(null));
        assertEquals("SelectionMode.FILES_AND_DIRECTORIES", action.arg().orElse(null));
    }

    @Test
    void shouldThrowExceptionForWhitespaceOnlyClassName() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="   " name="Mark Complete" />
                </actions>
                """;

        // When & Then - should fail fast for whitespace-only className
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    @Test
    void shouldThrowExceptionForWhitespaceOnlyName() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction" name="   " />
                </actions>
                """;

        // When & Then - should fail fast for whitespace-only name
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    @Test
    void shouldThrowExceptionForMultipleInvalidActions() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="" name="First Invalid" />
                    <action class="actions.ValidAction" name="Valid Action" />
                    <action name="Second Invalid" />
                </actions>
                """;

        // When & Then - should fail fast on first invalid action, not partially process
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    @Test
    void shouldParseValidShortcutWithMultipleMasksAndKeys() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction" name="Mark Complete">
                        <shortcut>
                            <mask keyname="menu" />
                            <mask keyname="shift" />
                            <key keyname="D" />
                        </shortcut>
                    </action>
                </actions>
                """;

        // Given - mock KeyboardManager
        when(keyboardManager.xmlKeynameToInternalForm("menu")).thenReturn("ctrl");
        when(keyboardManager.xmlKeynameToInternalForm("shift")).thenReturn("shift");
        when(keyboardManager.xmlKeynameToInternalForm("D")).thenReturn("D");

        // When
        List<ActionConfig> actions = parseFromString(xml);

        // Then
        assertEquals(1, actions.size());
        ActionConfig action = actions.get(0);
        assertTrue(action.shortcut().isPresent());
    }

    @Test
    void shouldParseValidShortcutWithOnlyKeys() throws Exception {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.PlayPauseAction" name="Play/Pause">
                        <shortcut>
                            <key keyname="SPACE" />
                        </shortcut>
                    </action>
                </actions>
                """;

        // Given - mock KeyboardManager
        when(keyboardManager.xmlKeynameToInternalForm("SPACE")).thenReturn("SPACE");

        // When
        List<ActionConfig> actions = parseFromString(xml);

        // Then
        assertEquals(1, actions.size());
        ActionConfig action = actions.get(0);
        assertTrue(action.shortcut().isPresent());
    }

    @Test
    void shouldThrowExceptionForMalformedXml() {
        // Given
        String malformedXml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction" name="Mark Complete">
                    <action class="actions.PlayPauseAction" name="Play/Pause" />
                </actions>
                """;

        // When & Then - should fail on malformed XML
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(malformedXml);
                });
    }

    @Test
    void shouldThrowExceptionForXmlWithUnexpectedElements() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <unexpectedElement />
                    <action class="actions.DoneAction" name="Mark Complete" />
                </actions>
                """;

        // When & Then - should fail on unknown elements
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    @Test
    void shouldTestAllPlatformTypes() throws Exception {
        // Given
        String xml =
                """
<?xml version="1.0"?>
<actions>
    <action class="actions.DoneAction" name="Mark Complete" os="Mac OS X" />
    <action class="actions.PlayPauseAction" name="Play/Pause" os="Windows" />
    <action class="actions.StopAction" name="Stop" os="Linux" />
</actions>
""";

        // Test macOS
        when(platform.detect()).thenReturn(Platform.PlatformType.MACOS);
        List<ActionConfig> macActions = parseFromString(xml);
        assertEquals(1, macActions.size());
        assertEquals("actions.DoneAction", macActions.get(0).className());

        // Test Windows
        when(platform.detect()).thenReturn(Platform.PlatformType.WINDOWS);
        List<ActionConfig> windowsActions = parseFromString(xml);
        assertEquals(1, windowsActions.size());
        assertEquals("actions.PlayPauseAction", windowsActions.get(0).className());

        // Test Linux
        when(platform.detect()).thenReturn(Platform.PlatformType.LINUX);
        List<ActionConfig> linuxActions = parseFromString(xml);
        assertEquals(1, linuxActions.size());
        assertEquals("actions.StopAction", linuxActions.get(0).className());
    }

    @Test
    void shouldThrowExceptionForMixedValidAndInvalidPlatforms() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction"
                            name="Mark Complete"
                            os="Linux,UnknownOS,Windows" />
                </actions>
                """;

        // When & Then - should fail fast on first invalid OS
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    @Test
    void shouldThrowExceptionForNullKeyElementKeyname() {
        // Given
        String xml =
                """
                <?xml version="1.0"?>
                <actions>
                    <action class="actions.DoneAction" name="Mark Complete">
                        <shortcut>
                            <key keyname="D" />
                            <key />
                        </shortcut>
                    </action>
                </actions>
                """;

        // When & Then - should fail fast for null keyname
        assertThrows(
                ActionsFileParser.ActionParseException.class,
                () -> {
                    parseFromString(xml);
                });
    }

    // Helper method to parse XML from string
    private List<ActionConfig> parseFromString(String xml)
            throws ActionsFileParser.ActionParseException {
        try {
            // Create a temporary file for testing
            Path tempFile = Files.createTempFile("test-actions", ".xml");
            Files.write(tempFile, xml.getBytes(StandardCharsets.UTF_8));
            URL fileUrl = tempFile.toUri().toURL();
            return parser.parseActions(fileUrl);
        } catch (IOException e) {
            throw new ActionsFileParser.ActionParseException("Failed to parse test XML", e);
        }
    }
}
