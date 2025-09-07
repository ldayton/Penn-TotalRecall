package ui;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import ui.annotations.AnnotationDisplay;
import ui.annotations.AnnotationTable;
import ui.audiofiles.AudioFileDisplay;
import ui.audiofiles.AudioFileList;
import ui.preferences.PreferencesFrame;
import ui.preferences.PreferencesManager;
import ui.wordpool.WordpoolDisplay;
import ui.wordpool.WordpoolList;
import ui.wordpool.WordpoolTextField;

/**
 * Guice module for the ui package.
 *
 * <p>Configures bindings for user interface components.
 */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        // Main window and frame components
        bind(MainFrame.class).in(Singleton.class);
        bind(AppMenuBar.class).in(Singleton.class);
        bind(ContentSplitPane.class).in(Singleton.class);
        bind(ControlPanel.class).in(Singleton.class);
        bind(DoneButton.class).in(Singleton.class);
        bind(WaveformCanvas.class).in(Singleton.class);

        // Window management and persistence
        bind(WindowLayoutPersistence.class).in(Singleton.class);
        bind(AppFocusTraversalPolicy.class).in(Singleton.class);

        // Preferences
        bind(PreferencesFrame.class).in(Singleton.class);
        bind(PreferencesManager.class).in(Singleton.class);

        // Audio file display
        bind(AudioFileDisplay.class).in(Singleton.class);
        bind(AudioFileList.class).in(Singleton.class);

        // Annotation display
        bind(AnnotationDisplay.class).in(Singleton.class);
        bind(AnnotationTable.class).in(Singleton.class);

        // Wordpool display
        bind(WordpoolDisplay.class).in(Singleton.class);
        bind(WordpoolList.class).in(Singleton.class);
        bind(WordpoolTextField.class).in(Singleton.class);

        // Utility services
        bind(DialogService.class).in(Singleton.class);
        bind(MainWindowAccess.class).in(Singleton.class);
        bind(KeyboardManager.class).in(Singleton.class);
        bind(LookAndFeelManager.class).in(Singleton.class);
    }
}
