package actions;

import com.google.common.reflect.ClassPath;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import core.actions.Action;
import core.actions.ActionRegistry;
import java.io.IOException;

/**
 * Guice module for the actions package.
 *
 * <p>Configures bindings for action management and parsing components. Also auto-discovers all
 * Action implementations and registers them for dependency injection.
 */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        // Action management
        bind(ActionsManager.class).in(Singleton.class);

        // Action file parsing
        bind(ActionsFileParser.class);

        // Auto-discover and bind all Action implementations
        Multibinder<Action> actionBinder = Multibinder.newSetBinder(binder(), Action.class);

        try {
            ClassPath classPath = ClassPath.from(getClass().getClassLoader());

            // Find all classes in the "actions" package that implement Action
            classPath.getTopLevelClasses("actions").stream()
                    .map(ClassPath.ClassInfo::load)
                    .filter(Action.class::isAssignableFrom)
                    .filter(clazz -> !clazz.isInterface())
                    .filter(clazz -> !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()))
                    .forEach(
                            actionClass -> {
                                @SuppressWarnings("unchecked")
                                Class<? extends Action> typedClass =
                                        (Class<? extends Action>) actionClass;
                                actionBinder.addBinding().to(typedClass);
                                bind(typedClass).in(Singleton.class);
                            });

        } catch (IOException e) {
            throw new RuntimeException("Failed to scan for Action implementations", e);
        }

        // Bind the action registry
        bind(ActionRegistry.class).in(Singleton.class);
    }
}
