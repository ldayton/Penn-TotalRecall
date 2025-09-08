package core.preferences;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/** Guice module for core preferences functionality. */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        bind(PreferencesManager.class).in(Singleton.class);
    }
}
