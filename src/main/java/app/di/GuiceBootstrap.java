package app.di;

import actions.ActionsManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import env.DevModeFileAutoLoader;
import env.LookAndFeelManager;
import env.UpdateManager;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.AppFocusTraversalPolicy;
import ui.AppMenuBar;
import ui.ContentSplitPane;
import ui.MainFrame;
import ui.WindowLayoutPersistence;

/**
 * Guice-based application bootstrap.
 *
 * <p>Handles dependency injection setup and application initialization with proper DI.
 */
public class GuiceBootstrap {

    private static Injector globalInjector;
    private static final Logger logger = LoggerFactory.getLogger(GuiceBootstrap.class);

    private final WindowLayoutPersistence windowManager;
    private final UpdateManager updateManager;
    private final LookAndFeelManager lookAndFeelManager;
    private final MainFrame myFrame;
    private final ContentSplitPane mySplitPane;
    private final AppFocusTraversalPolicy myFocusTraversalPolicy;

    @Inject
    public GuiceBootstrap(
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

    /** Creates the Guice injector and returns a bootstrapped application instance. */
    public static GuiceBootstrap create() {
        // Initialize Look and Feel BEFORE creating any Swing components
        // This is critical for Mac menu bar to work properly
        initializeLookAndFeelBeforeDI();

        // Set FlatLaf BEFORE creating any Swing components via DI
        setFlatLafBeforeDI();

        globalInjector = Guice.createInjector(new AppModule());

        // Initialize action configurations immediately after injector creation
        // This ensures action names and properties are available when components are created
        var actionsManager = globalInjector.getInstance(ActionsManager.class);
        actionsManager.initialize();

        // Get the bootstrap instance (this triggers creation of all DI-managed components)
        var bootstrap = globalInjector.getInstance(GuiceBootstrap.class);

        // WaveformMouseSetup disabled during refactoring
        // globalInjector.getInstance(ui.waveform.WaveformMouseSetup.class);

        // Initialize DevModeFileAutoLoader (subscribes to events)
        globalInjector.getInstance(DevModeFileAutoLoader.class);

        // Initialize waveform components
        globalInjector.getInstance(s2.WaveformManager.class);
        var paintDataSource = globalInjector.getInstance(s2.WaveformPaintDataSource.class);
        var painter = globalInjector.getInstance(w2.WaveformPainter.class);
        painter.setDataSource(paintDataSource);
        painter.start(); // Start the repaint timer

        // Initialize annotation manager to handle annotation events
        globalInjector.getInstance(s2.AnnotationManager.class);

        // AudioState is now fully managed by DI - no need to initialize CurAudio

        // Register all UpdatingAction instances with ActionsManager
        // This must happen after all components are created but before any UI updates
        AppMenuBar.registerAllActionsWithManager();

        // Register new actions with ActionsManager
        // This registers the new ADI-based actions
        registerNewActions(actionsManager);

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

    /**
     * Registers new ADI-based actions with the ActionsManager. This method registers all the new
     * actions that use dependency injection.
     */
    private static void registerNewActions(ActionsManager actionsManager) {
        // Register new actions that have been migrated to the actions package
        // These actions use dependency injection instead of static access

        // Get instances from the injector and register them
        var playPauseAction = globalInjector.getInstance(actions.PlayPauseAction.class);
        actionsManager.registerAction(playPauseAction);

        var stopAction = globalInjector.getInstance(actions.StopAction.class);
        actionsManager.registerAction(stopAction);

        var exitAction = globalInjector.getInstance(actions.ExitAction.class);
        actionsManager.registerAction(exitAction);

        var aboutAction = globalInjector.getInstance(actions.AboutAction.class);
        actionsManager.registerAction(aboutAction);

        var preferencesAction = globalInjector.getInstance(actions.PreferencesAction.class);
        actionsManager.registerAction(preferencesAction);

        // ReplayLast200MillisAction disabled - depends on SelectionOverlay
        // var replayLast200MillisAction =
        //         globalInjector.getInstance(actions.ReplayLast200MillisAction.class);
        // actionsManager.registerAction(replayLast200MillisAction);

        var returnToLastPositionAction =
                globalInjector.getInstance(actions.ReturnToLastPositionAction.class);
        actionsManager.registerAction(returnToLastPositionAction);

        var replayLastPositionAction =
                globalInjector.getInstance(actions.ReplayLastPositionAction.class);
        actionsManager.registerAction(replayLastPositionAction);

        var doneAction = globalInjector.getInstance(actions.DoneAction.class);
        actionsManager.registerAction(doneAction);

        var editShortcutsAction = globalInjector.getInstance(actions.EditShortcutsAction.class);
        actionsManager.registerAction(editShortcutsAction);

        var visitTutorialSiteAction =
                globalInjector.getInstance(actions.VisitTutorialSiteAction.class);
        actionsManager.registerAction(visitTutorialSiteAction);

        var tipsMessageAction = globalInjector.getInstance(actions.TipsMessageAction.class);
        actionsManager.registerAction(tipsMessageAction);

        // Register CheckUpdatesAction
        var checkUpdatesAction = globalInjector.getInstance(actions.CheckUpdatesAction.class);
        actionsManager.registerAction(checkUpdatesAction);

        // Register the newly migrated actions
        var continueAnnotatingAction =
                globalInjector.getInstance(actions.ContinueAnnotatingAction.class);
        actionsManager.registerAction(continueAnnotatingAction);

        var deleteAnnotationAction =
                globalInjector.getInstance(actions.DeleteAnnotationAction.class);
        actionsManager.registerAction(deleteAnnotationAction);

        // var deleteSelectedAnnotationAction =
        //         globalInjector.getInstance(actions.DeleteSelectedAnnotationAction.class);
        // actionsManager.registerAction(deleteSelectedAnnotationAction);

        var jumpToAnnotationAction =
                globalInjector.getInstance(actions.JumpToAnnotationAction.class);
        actionsManager.registerAction(jumpToAnnotationAction);

        var openWordpoolAction = globalInjector.getInstance(actions.OpenWordpoolAction.class);
        actionsManager.registerAction(openWordpoolAction);

        // Register annotation actions
        var annotateAction = globalInjector.getInstance(actions.AnnotateAction.class);
        actionsManager.registerAction(annotateAction);

        // Register remaining migrated actions
        var openAudioFileAction = globalInjector.getInstance(actions.OpenAudioFileAction.class);
        actionsManager.registerAction(openAudioFileAction);
        var openAudioFolderAction = globalInjector.getInstance(actions.OpenAudioFolderAction.class);
        actionsManager.registerAction(openAudioFolderAction);

        var seekAction = globalInjector.getInstance(actions.SeekAction.class);
        actionsManager.registerAction(seekAction);

        var screenSeekForwardAction =
                globalInjector.getInstance(actions.ScreenSeekForwardAction.class);
        actionsManager.registerAction(screenSeekForwardAction);

        var screenSeekBackwardAction =
                globalInjector.getInstance(actions.ScreenSeekBackwardAction.class);
        actionsManager.registerAction(screenSeekBackwardAction);

        var toggleAnnotationsAction =
                globalInjector.getInstance(actions.ToggleAnnotationsAction.class);
        actionsManager.registerAction(toggleAnnotationsAction);

        var last200PlusMoveAction = globalInjector.getInstance(actions.Last200PlusMoveAction.class);
        actionsManager.registerAction(last200PlusMoveAction);

        // var zoomInAction = globalInjector.getInstance(actions.ZoomInAction.class);
        // actionsManager.registerAction(zoomInAction);
        // var zoomOutAction = globalInjector.getInstance(actions.ZoomOutAction.class);
        // actionsManager.registerAction(zoomOutAction);
    }

    /** Initializes and starts the GUI application. */
    public void startApplication() {
        // Initialize Look and Feel with proper DI (includes macOS handlers)
        lookAndFeelManager.initialize();

        // ActionsManager already initialized during bootstrap
        myFrame.setFocusTraversalPolicy(myFocusTraversalPolicy);
        windowManager.restoreWindowLayout(myFrame, mySplitPane);

        // Update actions after UI is created (this sets button text and enabled states)
        // This was missing from the new DI system but present in the old Main class
        AppMenuBar.updateActions();

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
