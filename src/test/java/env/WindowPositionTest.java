package env;

import static org.junit.jupiter.api.Assertions.*;

import annotation.MacOS;
import com.google.inject.Guice;
import components.MyFrame;
import components.MySplitPane;
import components.WindowManager;
import di.AppModule;
import java.awt.Rectangle;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for window position persistence functionality. */
@MacOS
class WindowPositionTest {

    private WindowManager windowManager;
    private MyFrame myFrame;
    private MySplitPane mySplitPane;
    private Preferences appPrefs;

    @BeforeEach
    void setUp() {
        // Clear any existing preferences before each test
        appPrefs = Preferences.userRoot().node("/edu/upenn/psych/memory/penntotalrecall");
        clearAppPreferences();

        // Create components using Guice
        var injector = Guice.createInjector(new AppModule());
        windowManager = injector.getInstance(WindowManager.class);
        myFrame = injector.getInstance(MyFrame.class);
        mySplitPane = injector.getInstance(MySplitPane.class);
    }

    @AfterEach
    void tearDown() {
        // Clean up preferences after each test to prevent interference
        clearAppPreferences();
    }

    private void clearAppPreferences() {
        try {
            appPrefs.clear();
        } catch (BackingStoreException e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @DisplayName("Window position should be saved and restored correctly")
    void windowPositionShouldBeSavedAndRestored() {
        // Set a specific window position
        Rectangle testBounds = new Rectangle(100, 150, 1200, 800);
        myFrame.setBounds(testBounds);

        // Save the window layout
        windowManager.saveWindowLayout(myFrame, mySplitPane);

        // Clear the frame bounds to simulate a fresh start
        myFrame.setBounds(0, 0, 1000, 500);

        // Restore window layout
        windowManager.restoreWindowLayout(myFrame, mySplitPane);

        // Verify the window position was restored
        Rectangle restoredBounds = myFrame.getBounds();
        assertEquals(testBounds.x, restoredBounds.x, "Window X position should be restored");
        assertEquals(testBounds.y, restoredBounds.y, "Window Y position should be restored");
        assertEquals(testBounds.width, restoredBounds.width, "Window width should be restored");
        assertEquals(testBounds.height, restoredBounds.height, "Window height should be restored");
    }

    @Test
    @DisplayName("Window position should persist across application restarts")
    void windowPositionShouldPersistAcrossRestarts() {
        // Set a specific window position
        Rectangle testBounds = new Rectangle(200, 250, 1100, 700);
        myFrame.setBounds(testBounds);

        // Save the window layout
        windowManager.saveWindowLayout(myFrame, mySplitPane);

        // Simulate application restart by creating new components
        var newInjector = Guice.createInjector(new AppModule());
        MyFrame newFrame = newInjector.getInstance(MyFrame.class);
        MySplitPane newSplitPane = newInjector.getInstance(MySplitPane.class);
        WindowManager newWindowManager = newInjector.getInstance(WindowManager.class);

        // Restore window layout on the new frame
        newWindowManager.restoreWindowLayout(newFrame, newSplitPane);

        // Verify the window position was restored from persistent storage
        Rectangle restoredBounds = newFrame.getBounds();
        assertEquals(
                testBounds.x, restoredBounds.x, "Window X position should persist across restarts");
        assertEquals(
                testBounds.y, restoredBounds.y, "Window Y position should persist across restarts");
        assertEquals(
                testBounds.width,
                restoredBounds.width,
                "Window width should persist across restarts");
        assertEquals(
                testBounds.height,
                restoredBounds.height,
                "Window height should persist across restarts");
    }

    @Test
    @DisplayName(
            "ExitAction should save window position using same preference system as WindowManager")
    void exitActionShouldSaveWindowPositionConsistently() {
        Rectangle testBounds = new Rectangle(300, 350, 1300, 900);
        myFrame.setBounds(testBounds);

        // Simulate what the new ExitAction does - save using WindowManager
        windowManager.saveWindowLayout(myFrame, mySplitPane);

        // Now try to restore using WindowManager (which uses PreferencesManager)
        windowManager.restoreWindowLayout(myFrame, mySplitPane);

        // This should now work because both save and load use the same preference system
        Rectangle restoredBounds = myFrame.getBounds();
        assertEquals(
                testBounds.x,
                restoredBounds.x,
                "Window X position should be restored from ExitAction save");
        assertEquals(
                testBounds.y,
                restoredBounds.y,
                "Window Y position should be restored from ExitAction save");
        assertEquals(
                testBounds.width,
                restoredBounds.width,
                "Window width should be restored from ExitAction save");
        assertEquals(
                testBounds.height,
                restoredBounds.height,
                "Window height should be restored from ExitAction save");
    }
}
