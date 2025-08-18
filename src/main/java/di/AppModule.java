package di;

import actions.ActionsFileParser;
import actions.ActionsManager;
import audio.AudioSystemLoader;
import audio.AudioSystemManager;
import audio.FmodCore;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import components.WindowManager;
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
    }

    @Provides
    @Singleton
    HttpClient provideHttpClient() {
        return HttpClient.newHttpClient();
    }
}
