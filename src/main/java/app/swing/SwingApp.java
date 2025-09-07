package app.swing;

import actions.ActionsManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import env.LookAndFeelManager;
import env.UpdateManager;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.AppFocusTraversalPolicy;
import ui.ContentSplitPane;
import ui.MainFrame;
import ui.WindowLayoutPersistence;

/**
 * Swing-based desktop application entry point.
 *
 * <p>Handles dependency injection setup and application initialization for the Swing UI.
 */
public class SwingApp {

    private static Injector globalInjector;
    private static final Logger logger = LoggerFactory.getLogger(SwingApp.class);

    private final WindowLayoutPersistence windowManager;
    private final UpdateManager updateManager;
    private final LookAndFeelManager lookAndFeelManager;
    private final MainFrame myFrame;
    private final ContentSplitPane mySplitPane;
    private final AppFocusTraversalPolicy myFocusTraversalPolicy;

    @Inject
    public SwingApp(
            WindowLayoutPersistence windowManager,
            UpdateManager updateManager,
            LookAndFeelManager lookAndFeelManager,
            MainFrame myFrame,
            ContentSplitPane mySplitPane,
            AppFocusTraversalPolicy myFocusTraversalPolicy) {
        this.windowManager = windowManager;
        this.updateManager = updateManager;
        this.lookAndFeelManager = lookAndFeelManager;
        this.myFrame = myFrame;
        this.mySplitPane = mySplitPane;
        this.myFocusTraversalPolicy = myFocusTraversalPolicy;
    }

    /** Creates the Guice injector and returns a Swing application instance. */
    public static SwingApp create() {
        // Initialize Look and Feel BEFORE creating any Swing components
        // This is critical for Mac menu bar to work properly
        initializeLookAndFeelBeforeDI();

        // Set FlatLaf BEFORE creating any Swing components via DI
        setFlatLafBeforeDI();

        globalInjector = Guice.createInjector(new SwingModule());

        // Initialize action configurations immediately after injector creation
        // This ensures action names and properties are available when components are created
        var actionsManager = globalInjector.getInstance(ActionsManager.class);
        actionsManager.initialize();

        // Get the bootstrap instance (this triggers creation of all DI-managed components)
        var bootstrap = globalInjector.getInstance(SwingApp.class);

        // WaveformMouseSetup disabled during refactoring
        // globalInjector.getInstance(ui.waveform.WaveformMouseSetup.class);

        // Initialize DevModeFileAutoLoader (subscribes to events)
        globalInjector.getInstance(DevModeFileAutoLoader.class);

        // Initialize waveform components
        globalInjector.getInstance(state.WaveformManager.class);
        var paintDataSource = globalInjector.getInstance(state.WaveformPaintDataSource.class);
        var painter = globalInjector.getInstance(waveform.WaveformPainter.class);
        painter.setDataSource(paintDataSource);
        painter.start(); // Start the repaint timer

        // Initialize annotation manager to handle annotation events
        globalInjector.getInstance(state.AnnotationManager.class);

        // Initialize browser launcher to handle URL open requests
        globalInjector.getInstance(ui.BrowserLauncher.class);

        // AudioState is now fully managed by DI - no need to initialize CurAudio

        // Initialize ActionRegistry which auto-registers all actions
        // The registry is created by Guice with all actions auto-discovered and injected
        globalInjector.getInstance(core.actions.ActionRegistry.class);

        return bootstrap;
    }

    /**
     * Initializes Look and Feel before dependency injection to ensure platform-specific properties
     * (like Mac menu bar) are set before any Swing components are created.
     *
     * <p>During bootstrap, we only set up the essential system properties needed for Mac menu bar.
     * The full LookAndFeelManager with proper DI will be created later.
     */
    private static void initializeLookAndFeelBeforeDI() {
        // Set essential Mac menu bar properties before any Swing components are created
        // This is the minimal setup needed for Mac menu bar to work properly
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.textantialiasing", "on");
            System.setProperty("apple.awt.antialiasing", "on");
            System.setProperty("apple.awt.rendering", "quality");
            System.setProperty("apple.awt.application.appearance", "system");
            // Load app name from config since this runs before DI
            env.AppConfig tempConfig = new env.AppConfig();
            System.setProperty(
                    "apple.awt.application.name",
                    tempConfig.getProperty(env.AppConfig.APP_NAME_KEY));
        }
    }

    /**
     * Sets FlatLaf before any Swing components are created via DI. This ensures all components use
     * FlatLaf from the start.
     */
    private static void setFlatLafBeforeDI() {
        try {
            com.formdev.flatlaf.FlatLightLaf.setup();
            logger.info("Set FlatLaf before DI initialization");
        } catch (Exception e) {
            logger.error("Failed to set FlatLaf before DI: {}", e.getMessage());
            throw new RuntimeException("Failed to set FlatLaf before DI initialization", e);
        }
    }

    /**
     * Gets an injected instance of the specified class from the global injector.
     *
     * @param clazz The class to get an instance of
     * @return The injected instance
     * @throws IllegalStateException if no injector is available
     */
    public static <T> T getInjectedInstance(Class<T> clazz) {
        if (globalInjector != null) {
            return globalInjector.getInstance(clazz);
        }
        throw new IllegalStateException(
                "DI injector not available. Ensure GuiceBootstrap.create() was called first.");
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
        // Initialize Look and Feel with proper DI (includes macOS handlers)
        lookAndFeelManager.initialize();

        // ActionsManager already initialized during bootstrap
        myFrame.setFocusTraversalPolicy(myFocusTraversalPolicy);
        windowManager.restoreWindowLayout(myFrame, mySplitPane);

        myFrame.setVisible(true);

        // Wait for UI to be fully ready before publishing UIReadyEvent
        // This ensures canvas is sized and initial paint has occurred
        var canvas = globalInjector.getInstance(ui.WaveformCanvas.class);
        var eventBus = globalInjector.getInstance(events.EventDispatchBus.class);

        javax.swing.Timer readyTimer =
                new javax.swing.Timer(
                        100,
                        e -> {
                            if (canvas.isShowing()
                                    && canvas.getWidth() > 0
                                    && canvas.getHeight() > 0) {
                                // UI is ready - canvas is visible and sized
                                ((javax.swing.Timer) e.getSource()).stop();
                                eventBus.publish(new events.UIReadyEvent());
                                logger.info("UI is ready - publishing UIReadyEvent");
                            }
                        });
        readyTimer.setRepeats(true);
        readyTimer.start();

        // Check for updates after UI is ready (async, non-blocking)
        updateManager.checkForUpdateOnStartup();
    }
}
