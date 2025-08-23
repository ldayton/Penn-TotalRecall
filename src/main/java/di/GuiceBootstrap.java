package di;

import actions.ActionsManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import components.MyFocusTraversalPolicy;
import components.MyFrame;
import components.MyMenu;
import components.MySplitPane;
import components.WindowManager;
import env.LookAndFeelManager;
import env.UpdateManager;
import jakarta.inject.Inject;

/**
 * Guice-based application bootstrap.
 *
 * <p>Handles dependency injection setup and application initialization with proper DI.
 */
public class GuiceBootstrap {

    private static Injector globalInjector;

    private final WindowManager windowManager;
    private final UpdateManager updateManager;
    private final LookAndFeelManager lookAndFeelManager;
    private final ActionsManager actionsManager;
    private final MyMenu myMenu;
    private final MyFrame myFrame;
    private final MySplitPane mySplitPane;
    private final MyFocusTraversalPolicy myFocusTraversalPolicy;

    @Inject
    public GuiceBootstrap(
            WindowManager windowManager,
            UpdateManager updateManager,
            LookAndFeelManager lookAndFeelManager,
            ActionsManager actionsManager,
            MyMenu myMenu,
            MyFrame myFrame,
            MySplitPane mySplitPane,
            MyFocusTraversalPolicy myFocusTraversalPolicy) {
        this.windowManager = windowManager;
        this.updateManager = updateManager;
        this.lookAndFeelManager = lookAndFeelManager;
        this.actionsManager = actionsManager;
        this.myMenu = myMenu;
        this.myFrame = myFrame;
        this.mySplitPane = mySplitPane;
        this.myFocusTraversalPolicy = myFocusTraversalPolicy;
    }

    /** Creates the Guice injector and returns a bootstrapped application instance. */
    public static GuiceBootstrap create() {
        globalInjector = Guice.createInjector(new AppModule());
        return globalInjector.getInstance(GuiceBootstrap.class);
    }

    /**
     * Gets an injected instance of the specified class from the global injector.
     *
     * @param clazz The class to get an instance of
     * @return The injected instance, or null if no injector is available
     */
    public static <T> T getInjectedInstance(Class<T> clazz) {
        if (globalInjector != null) {
            return globalInjector.getInstance(clazz);
        }
        return null;
    }

    /** Initializes and starts the GUI application. */
    public void startApplication() {
        lookAndFeelManager.initialize();
        actionsManager.initialize(); // Load action configuration before UI creation
        myFrame.setFocusTraversalPolicy(myFocusTraversalPolicy);
        windowManager.restoreWindowLayout(myFrame, mySplitPane);
        myFrame.setVisible(true);

        // Check for updates after UI is ready (async, non-blocking)
        updateManager.checkForUpdateOnStartup();
    }
}
