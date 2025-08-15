package di;

import com.google.inject.Guice;
import com.google.inject.Injector;
import components.MyFocusTraversalPolicy;
import components.MyFrame;
import components.MyMenu;
import components.MySplitPane;
import components.WindowManager;
import env.Environment;
import jakarta.inject.Inject;
import util.UpdateChecker;

/**
 * Guice-based application bootstrap.
 *
 * <p>Handles dependency injection setup and application initialization with proper DI.
 */
public class GuiceBootstrap {

    private final Environment environment;
    private final WindowManager windowManager;
    private final UpdateChecker updateChecker;

    @Inject
    public GuiceBootstrap(
            Environment environment, WindowManager windowManager, UpdateChecker updateChecker) {
        this.environment = environment;
        this.windowManager = windowManager;
        this.updateChecker = updateChecker;
    }

    /** Creates the Guice injector and returns a bootstrapped application instance. */
    public static GuiceBootstrap create() {
        Injector injector = Guice.createInjector(new AppModule());
        return injector.getInstance(GuiceBootstrap.class);
    }

    /** Initializes and starts the GUI application. */
    public void startApplication() {
        environment.initializeLookAndFeel();
        var mainFrame = MyFrame.createInstance(environment);
        mainFrame.setFocusTraversalPolicy(new MyFocusTraversalPolicy());
        MyMenu.updateActions();
        windowManager.restoreWindowLayout(mainFrame, MySplitPane.getInstance());
        mainFrame.setVisible(true);

        // Check for updates after UI is ready (async, non-blocking)
        updateChecker.checkForUpdateOnStartup();
    }
}
