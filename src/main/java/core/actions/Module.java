package core.actions;

import com.google.common.reflect.ClassPath;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import java.io.IOException;

/**
 * Guice module for core actions.
 *
 * <p>Auto-discovers all Action implementations in the core.actions package and registers them for
 * dependency injection.
 */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        // Auto-discover and bind all Action implementations in core.actions
        Multibinder<Action> actionBinder = Multibinder.newSetBinder(binder(), Action.class);

        try {
            ClassPath classPath = ClassPath.from(getClass().getClassLoader());

            // Find all classes in the "core.actions.impl" package that extend Action
            classPath.getTopLevelClasses("core.actions.impl").stream()
                    .map(ClassPath.ClassInfo::load)
                    .filter(Action.class::isAssignableFrom)
                    .filter(clazz -> !clazz.isInterface())
                    .filter(clazz -> !clazz.equals(Action.class))
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
            throw new RuntimeException("Failed to scan for core Action implementations", e);
        }

        // Bind the action registry
        bind(ActionRegistry.class).in(Singleton.class);
    }
}
