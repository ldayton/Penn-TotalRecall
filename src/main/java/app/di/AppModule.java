package app.di;

import a2.fmod.FmodLibraryLoader;
import a2.fmod.FmodModule;
import actions.ActionsFileParser;
import actions.ActionsManager;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import env.AppConfig;
import env.DevModeFileAutoLoader;
import env.KeyboardManager;
import env.LookAndFeelManager;
import env.Platform;
import env.ProgramVersion;
import env.UpdateManager;
import env.UserHomeProvider;
import jakarta.inject.Singleton;
import java.net.http.HttpClient;
import s2.AudioSessionManager;
import s2.AudioSessionStateMachine;
import s2.WaveformManager;
import s2.WaveformPaintDataSource;
import s2.WaveformSessionDataSource;
import s2.WaveformViewport;
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
import w2.WaveformPainter;

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

        // Configure s2 package bindings
        bind(AudioSessionManager.class).in(Singleton.class);
        bind(AudioSessionStateMachine.class).in(Singleton.class);
        bind(WaveformSessionDataSource.class).to(AudioSessionManager.class).in(Singleton.class);
        bind(WaveformPainter.class).in(Singleton.class);
        bind(WaveformManager.class).in(Singleton.class);
        bind(WaveformViewport.class).in(Singleton.class);
        bind(WaveformPaintDataSource.class).in(Singleton.class);

        bind(ActionsFileParser.class);
        bind(ActionsManager.class).in(Singleton.class);
        bind(AppConfig.class).in(Singleton.class);
        bind(FmodLibraryLoader.class).in(Singleton.class);
        bind(DevModeFileAutoLoader.class).in(Singleton.class);
        bind(KeyboardManager.class).in(Singleton.class);
        bind(LookAndFeelManager.class).in(Singleton.class);
        bind(Platform.class).in(Singleton.class);
        bind(PreferencesManager.class).in(Singleton.class);
        bind(UpdateManager.class).in(Singleton.class);
        bind(UserHomeProvider.class).in(Singleton.class);
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
        bind(ProgramVersion.class).in(Singleton.class);
        bind(DialogService.class).in(Singleton.class);
        bind(MainWindowAccess.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    HttpClient provideHttpClient() {
        return HttpClient.newHttpClient();
    }
}
