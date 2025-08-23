package di;

import actions.ActionsManager;
import behaviors.singleact.ExitAction;
import com.google.inject.Guice;
import com.google.inject.Injector;
import components.MyFocusTraversalPolicy;
import components.MyFrame;
import components.MyMenu;
import components.MySplitPane;
import components.ShortcutFrame;
import components.WindowManager;
import env.AppConfig;
import env.LookAndFeelManager;
import env.Platform;
import env.UpdateManager;
import env.UserManager;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guice-based application bootstrap.
 *
 * <p>Handles dependency injection setup and application initialization with proper DI.
 */
public class GuiceBootstrap {

    private static Injector globalInjector;
    private static final Logger logger = LoggerFactory.getLogger(GuiceBootstrap.class);

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
        // Initialize Look and Feel BEFORE creating any Swing components
        // This is critical for Mac menu bar to work properly
        initializeLookAndFeelBeforeDI();

        globalInjector = Guice.createInjector(new AppModule());
        return globalInjector.getInstance(GuiceBootstrap.class);
    }

    /**
     * Initializes Look and Feel before dependency injection to ensure platform-specific properties
     * (like Mac menu bar) are set before any Swing components are created.
     *
     * <p>During bootstrap, we create minimal instances of required components without full DI
     * injection. The ExitAction is created with a null PreferencesManager which is handled
     * gracefully in its actionPerformed method by providing default values and conditionally
     * calling preference methods.
     */
    private static void initializeLookAndFeelBeforeDI() {
        // Create minimal instances needed for Look and Feel initialization
        Platform platform = new Platform();
        UserManager userManager = new UserManager();
        AppConfig appConfig = new AppConfig(platform, userManager);

        // Create a minimal ExitAction for LookAndFeelManager (before DI is available)
        // This is only used for Mac menu bar setup, not for actual exit functionality
        // The null PreferencesManager is handled gracefully in ExitAction.actionPerformed()
        // by providing default values and conditionally calling preference methods
        ExitAction exitAction =
                new ExitAction(null); // null PreferencesManager - will be replaced by DI later

        LookAndFeelManager lookAndFeelManager =
                new LookAndFeelManager(appConfig, platform, exitAction);

        // Initialize Look and Feel (this sets Mac menu bar properties)
        lookAndFeelManager.initialize();
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

    /**
     * Gets an injected instance of the specified class from the global injector, throwing an
     * IllegalStateException if the instance is not available.
     *
     * <p>This is a convenience method for components that require DI-managed instances and should
     * fail fast if they're not available.
     *
     * @param clazz The class to get an instance of
     * @param componentName A descriptive name for the component being requested (for error
     *     messages)
     * @return The injected instance
     * @throws IllegalStateException if the instance is not available
     */
    public static <T> T getRequiredInjectedInstance(Class<T> clazz, String componentName) {
        T instance = getInjectedInstance(clazz);
        if (instance == null) {
            throw new IllegalStateException(
                    componentName
                            + " not available via DI. Ensure GuiceBootstrap.create() was called"
                            + " first.");
        }
        return instance;
    }

    /** Initializes and starts the GUI application. */
    public void startApplication() {
        // Look and Feel already initialized before DI creation

        // Initialize XActionManager with actions.xml (this loads action configurations)
        // This is what ShortcutFrame.createDefault() does in the old system
        initializeXActionManager();

        actionsManager.initialize(); // Load action configuration before UI creation
        myFrame.setFocusTraversalPolicy(myFocusTraversalPolicy);
        windowManager.restoreWindowLayout(myFrame, mySplitPane);

        // Update actions after UI is created (this sets button text and enabled states)
        // This was missing from the new DI system but present in the old Main class
        MyMenu.updateActions();

        myFrame.setVisible(true);

        // Check for updates after UI is ready (async, non-blocking)
        updateManager.checkForUpdateOnStartup();
    }

    /**
     * Initializes the XActionManager by loading actions.xml. This is equivalent to what
     * ShortcutFrame.createDefault() does in the old system.
     */
    private void initializeXActionManager() {
        try {
            // Load actions.xml and initialize XActionManager
            // This populates the XActionManager with action configurations
            ShortcutFrame.createDefault();
        } catch (Exception e) {
            logger.error("Failed to initialize XActionManager with actions.xml", e);
        }
    }
}
