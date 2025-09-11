package ui.actions;

import com.google.inject.AbstractModule;

public class Module extends AbstractModule {
    @Override
    protected void configure() {
        bind(ActionManager.class).asEagerSingleton();
    }
}
