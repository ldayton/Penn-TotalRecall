package di;

import audio.FmodCore;
import audio.FmodLibraryLoader;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import components.WindowManager;
import env.AppConfig;
import env.Environment;
import env.KeyboardManager;
import env.LookAndFeelManager;
import info.DefaultPreferencesProvider;
import info.PreferencesProvider;
import jakarta.inject.Singleton;
import java.net.http.HttpClient;
import util.UpdateChecker;

/**
 * Guice module for dependency injection configuration.
 *
 * <p>Binds modern, constructor-injected classes for update checking and window management. FMOD
 * classes remain static for now to avoid complex migration.
 */
public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        // Singleton bindings for core services
        bind(AppConfig.class).in(Singleton.class);
        bind(Environment.class).in(Singleton.class);

        // Audio system bindings
        bind(FmodLibraryLoader.class).in(Singleton.class);
        bind(FmodCore.class).in(Singleton.class);

        // UI system bindings
        bind(LookAndFeelManager.class).in(Singleton.class);
        bind(KeyboardManager.class).in(Singleton.class);

        // Provider bindings
        bind(PreferencesProvider.class).to(DefaultPreferencesProvider.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    HttpClient provideHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Provides
    UpdateChecker provideUpdateChecker(AppConfig config, HttpClient httpClient) {
        return new UpdateChecker(config, httpClient);
    }

    @Provides
    WindowManager provideWindowManager(PreferencesProvider prefs) {
        return new WindowManager(prefs);
    }
}
