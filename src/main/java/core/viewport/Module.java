package core.viewport;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/** Guice module for viewport components. */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        // Viewport session management
        bind(ViewportSessionManager.class).in(Singleton.class);

        // Bind the ViewportPaintingDataSource interface to ViewportSessionManager
        bind(ViewportPaintingDataSource.class).to(ViewportSessionManager.class).in(Singleton.class);

        // Bind projector implementation
        bind(ViewportProjector.class).to(DefaultViewportProjector.class).in(Singleton.class);
    }
}
