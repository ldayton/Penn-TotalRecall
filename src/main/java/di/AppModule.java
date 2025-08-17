package di;

import audio.FmodCore;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import components.WindowManager;
import env.AppConfig;
import env.AudioSystemLoader;
import env.AudioSystemManager;
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
        // Core platform detection - must be first for other services to use
        bind(Platform.class).in(Singleton.class);

        // Singleton bindings for core services
        bind(AppConfig.class).in(Singleton.class);

        // Manager bindings - each handles specific domain configuration
        bind(AudioSystemLoader.class).to(AudioSystemManager.class).in(Singleton.class);
        bind(LookAndFeelManager.class).in(Singleton.class);
        bind(KeyboardManager.class).in(Singleton.class);
        bind(PreferencesManager.class).in(Singleton.class);
        bind(UpdateManager.class).in(Singleton.class);
        bind(UserManager.class).in(Singleton.class);

        // Audio system bindings
        bind(FmodCore.class).in(Singleton.class);

    }

    @Provides
    @Singleton
    HttpClient provideHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Provides
    WindowManager provideWindowManager(PreferencesManager prefs) {
        return new WindowManager(prefs);
    }
}
