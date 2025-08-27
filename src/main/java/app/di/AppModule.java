package app.di;

import actions.ActionsFileParser;
import actions.ActionsManager;
import audio.AudioSystemLoader;
import audio.AudioSystemManager;
import audio.FmodCore;
import audio.signal.AudioRenderer;
import audio.signal.Resampler;
import audio.signal.SampleMath;
import audio.signal.WaveformProcessor;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import components.AppFocusTraversalPolicy;
import components.AppMenuBar;
import components.ContentSplitPane;
import components.ControlPanel;
import components.DoneButton;
import components.MainFrame;
import components.WindowLayoutPersistence;
import components.annotations.AnnotationDisplay;
import components.annotations.AnnotationTable;
import components.audiofiles.AudioFileDisplay;
import components.audiofiles.AudioFileList;
import components.preferences.PreferencesFrame;
import components.waveform.SelectionOverlay;
import components.waveform.WaveformDisplay;
import components.wordpool.WordpoolDisplay;
import components.wordpool.WordpoolList;
import components.wordpool.WordpoolTextField;
import env.AppConfig;
import env.KeyboardManager;
import env.LookAndFeelManager;
import env.Platform;
import env.ProgramVersion;
import env.UpdateManager;
import env.UserHomeProvider;
import graphics.WaveformRenderer;
import graphics.WaveformScaler;
import jakarta.inject.Singleton;
import java.net.http.HttpClient;
import state.PreferencesManager;
import ui.DialogService;
import ui.MainWindowAccess;

/**
 * Guice module for dependency injection configuration.
 *
 * <p>Binds modern, constructor-injected classes for update checking and window management. FMOD
 * classes remain static for now to avoid complex migration.
 */
public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ActionsFileParser.class);
        bind(ActionsManager.class).in(Singleton.class);
        bind(AppConfig.class).in(Singleton.class);
        bind(AudioSystemLoader.class).to(AudioSystemManager.class).in(Singleton.class);
        bind(FmodCore.class).in(Singleton.class);
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
        bind(SelectionOverlay.class).in(Singleton.class);
        bind(PreferencesFrame.class).in(Singleton.class);
        bind(WaveformDisplay.class).in(Singleton.class);
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

        // Signal processing utilities
        bind(AudioRenderer.class).in(Singleton.class);
        bind(Resampler.class).in(Singleton.class);
        bind(SampleMath.class).in(Singleton.class);
        bind(WaveformProcessor.class).in(Singleton.class);

        // Graphics utilities
        bind(WaveformRenderer.class).in(Singleton.class);
        bind(WaveformScaler.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    HttpClient provideHttpClient() {
        return HttpClient.newHttpClient();
    }
}
