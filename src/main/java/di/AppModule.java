package di;

import actions.ActionsFileParser;
import actions.ActionsManager;
import audio.AudioSystemLoader;
import audio.AudioSystemManager;
import audio.FmodCore;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import components.ControlPanel;
import components.DoneButton;
import components.MyFrame;
import components.MyMenu;
import components.MySplitPane;
import components.WindowManager;
import components.annotations.AnnotationDisplay;
import components.annotations.AnnotationTable;
import components.audiofiles.AudioFileDisplay;
import components.audiofiles.AudioFileList;
import components.preferences.PreferencesFrame;
import components.waveform.MyGlassPane;
import components.waveform.WaveformDisplay;
import components.wordpool.WordpoolDisplay;
import components.wordpool.WordpoolList;
import components.wordpool.WordpoolTextField;
import env.AppConfig;
import env.KeyboardManager;
import env.LookAndFeelManager;
import env.Platform;
import env.PreferencesManager;
import env.UpdateManager;
import env.UserManager;
import jakarta.inject.Singleton;
import java.net.http.HttpClient;

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
        bind(UserManager.class).in(Singleton.class);
        bind(WindowManager.class).in(Singleton.class);
        bind(MyFrame.class).in(Singleton.class);
        bind(MyMenu.class).in(Singleton.class);
        bind(MySplitPane.class).in(Singleton.class);
        bind(ControlPanel.class).in(Singleton.class);
        bind(DoneButton.class).in(Singleton.class);
        bind(MyGlassPane.class).in(Singleton.class);
        bind(PreferencesFrame.class).in(Singleton.class);
        bind(WaveformDisplay.class).in(Singleton.class);
        bind(AudioFileDisplay.class).in(Singleton.class);
        bind(AnnotationDisplay.class).in(Singleton.class);
        bind(WordpoolDisplay.class).in(Singleton.class);
        bind(WordpoolTextField.class).in(Singleton.class);
        bind(WordpoolList.class).in(Singleton.class);
        bind(AnnotationTable.class).in(Singleton.class);
        bind(AudioFileList.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    HttpClient provideHttpClient() {
        return HttpClient.newHttpClient();
    }
}
