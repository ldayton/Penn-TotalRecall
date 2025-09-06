package app.di;

import a2.fmod.FmodLibraryLoader;
import a2.fmod.FmodModule;
import actions.ActionsFileParser;
import actions.ActionsManager;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;
import java.net.http.HttpClient;
import ui.AppFocusTraversalPolicy;
import ui.AppMenuBar;
import ui.ContentSplitPane;
import ui.ControlPanel;
import ui.DialogService;
import ui.DoneButton;
import ui.MainFrame;
import ui.MainWindowAccess;
import ui.WaveformCanvas;
import ui.WindowLayoutPersistence;
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
 * Guice module for dependency injection configuration.
 *
 * <p>Binds modern, constructor-injected classes for update checking and window management. FMOD
 * classes remain static for now to avoid complex migration.
 */
public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        // Install FMOD module for audio system dependencies
        install(new FmodModule());

        // Install a2 module for audio engine bindings
        install(new a2.Module());

        // Install w2 module for waveform bindings
        install(new w2.Module());

        // Install s2 module for session management bindings
        install(new s2.Module());

        // Install env module for environment and platform bindings
        install(new env.Module());

        bind(ActionsFileParser.class);
        bind(ActionsManager.class).in(Singleton.class);
        bind(FmodLibraryLoader.class).in(Singleton.class);
        bind(PreferencesManager.class).in(Singleton.class);
        bind(WindowLayoutPersistence.class).in(Singleton.class);
        bind(MainFrame.class).in(Singleton.class);
        bind(AppMenuBar.class).in(Singleton.class);
        bind(ContentSplitPane.class).in(Singleton.class);
        bind(ControlPanel.class).in(Singleton.class);
        bind(DoneButton.class).in(Singleton.class);
        bind(PreferencesFrame.class).in(Singleton.class);
        bind(WaveformCanvas.class).in(Singleton.class);
        bind(AudioFileDisplay.class).in(Singleton.class);
        bind(AnnotationDisplay.class).in(Singleton.class);
        bind(WordpoolDisplay.class).in(Singleton.class);
        bind(WordpoolTextField.class).in(Singleton.class);
        bind(WordpoolList.class).in(Singleton.class);
        bind(AnnotationTable.class).in(Singleton.class);
        bind(AudioFileList.class).in(Singleton.class);
        bind(AppFocusTraversalPolicy.class).in(Singleton.class);

        // Utility services
        bind(DialogService.class).in(Singleton.class);
        bind(MainWindowAccess.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    HttpClient provideHttpClient() {
        return HttpClient.newHttpClient();
    }
}
