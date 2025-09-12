package ui;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import ui.adapters.SwingEventDispatcher;
import ui.adapters.SwingFileSelectionService;
import ui.annotations.AnnotationDisplay;
import ui.annotations.AnnotationTable;
import ui.audiofiles.AudioFileDisplay;
import ui.audiofiles.AudioFileDisplayInterface;
import ui.audiofiles.AudioFileList;
import ui.layout.AppFocusTraversalPolicy;
import ui.layout.AppMenuBar;
import ui.layout.ContentSplitPane;
import ui.layout.ControlPanel;
import ui.layout.MainFrame;
import ui.layout.MainWindowAccess;
import ui.layout.WaveformCanvas;
import ui.layout.WindowLayoutPersistence;
import ui.preferences.PreferencesFrame;
import ui.waveform.WaveformPainter;
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
        // Install UI actions module
        install(new ui.actions.Module());

        // Services
        bind(core.services.FileSelectionService.class)
                .to(SwingFileSelectionService.class)
                .in(Singleton.class);

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
        bind(ShortcutFrame.class).in(Singleton.class);

        // Waveform
        bind(WaveformPainter.class).in(Singleton.class);

        // Audio file display
        bind(AudioFileDisplay.class).in(Singleton.class);
        bind(AudioFileDisplayInterface.class).to(AudioFileDisplay.class);
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

        // Event dispatch binding for Swing
        bind(core.dispatch.EventDispatcher.class)
                .to(SwingEventDispatcher.class)
                .in(Singleton.class);
    }
}
