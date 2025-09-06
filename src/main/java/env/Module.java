package env;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.net.http.HttpClient;

/**
 * Guice module for the env (environment) package.
 *
 * <p>Configures bindings for environment and platform-related components.
 */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        // Core environment and configuration
        bind(AppConfig.class).in(Singleton.class);
        bind(Platform.class).in(Singleton.class);
        bind(ProgramVersion.class).in(Singleton.class);
        bind(UserHomeProvider.class).in(Singleton.class);

        // UI environment management
        bind(KeyboardManager.class).in(Singleton.class);
        bind(LookAndFeelManager.class).in(Singleton.class);

        // Update checking
        bind(UpdateManager.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    HttpClient provideHttpClient() {
        return HttpClient.newHttpClient();
    }
}
