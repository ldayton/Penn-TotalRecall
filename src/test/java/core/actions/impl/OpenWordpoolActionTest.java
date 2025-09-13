package core.actions.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.swing.SwingTestFixture;
import core.dispatch.EventDispatchBus;
import core.preferences.PreferencesManager;
import core.services.FileSelectionService;
import core.services.FileSelectionService.FileSelectionRequest;
import java.io.File;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ui.wordpool.WordpoolList;

/**
 * Verifies that invoking OpenWordpoolAction results in the wordpool list being populated.
 *
 * <p>This test avoids dev-mode autoloading and drives the loading through the action itself using a
 * test FileSelectionService that returns the sample wordpool file.
 */
class OpenWordpoolActionTest extends SwingTestFixture {

    private static class TestFileSelectionService implements FileSelectionService {
        private final File file;

        TestFileSelectionService(File file) {
            this.file = file;
        }

        @Override
        public Optional<File> selectFile(FileSelectionRequest request) {
            return Optional.of(file);
        }

        @Override
        public File[] selectMultipleFiles(FileSelectionRequest request) {
            return new File[] {file};
        }
    }

    @Test
    void openWordpoolPopulatesDisplay() throws Exception {
        // Arrange: sample wordpool from test resources
        File wordpool = new File("src/test/resources/audio/wordpool.txt");
        assertTrue(wordpool.exists(), "Sample wordpool file should exist for the test");

        // Use DI-managed services but inject a test FileSelectionService
        PreferencesManager prefs = getInstance(PreferencesManager.class);
        core.env.UserHomeProvider home = getInstance(core.env.UserHomeProvider.class);
        EventDispatchBus bus = getInstance(EventDispatchBus.class);
        FileSelectionService testSelector = new TestFileSelectionService(wordpool);

        OpenWordpoolAction action = new OpenWordpoolAction(prefs, home, bus, testSelector);

        // Act: invoke the action (on EDT for UI consistency)
        onEdt(action::execute);

        // Allow a brief moment for event handling/rendering
        Thread.sleep(100);

        // Assert: wordpool list has entries
        WordpoolList list = getInstance(WordpoolList.class);
        assertTrue(
                list.getModel().getSize() > 0,
                "Wordpool list should be populated after OpenWordpoolAction");
    }
}
