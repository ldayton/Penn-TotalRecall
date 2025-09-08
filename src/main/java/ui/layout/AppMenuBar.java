package ui.layout;

import core.actions.AboutAction;
// import actions.AnnotateIntrusionAction;
// import actions.AnnotateRegularAction;
import core.actions.CheckUpdatesAction;
import core.actions.DoneAction;
import core.actions.EditShortcutsAction;
import core.actions.ExitAction;
import core.actions.Last200PlusMoveAction;
import core.actions.OpenWordpoolAction;
import core.actions.PlayPauseAction;
import core.actions.PreferencesAction;
import core.actions.ReplayLast200MillisAction;
// import actions.ReplayLastPositionAction;
// import actions.ReturnToLastPositionAction;
import core.actions.SeekToStartAction;
import core.actions.TipsMessageAction;
import core.actions.VisitTutorialSiteAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.adapters.SwingAction;

/**
 * Program menu bar and trigger for updates in program's state that concern program actions.
 *
 * <p>Most of the program's user actions are tied to menu items in this <code>JMenu</code>.
 * Programmers adding new functionality should in almost all cases add a corresponding <code>
 * JMenuItem</code> here. However, there are those cases where an Action should only be initiated in
 * another way (e.g., hitting enter in a text field), for which a menu item is inappropriate.
 *
 * <p>AppMenuBar keeps track of all {@link UpdatingAction} instances registered with it. Any time a
 * change occurs in program state that might cause certain actions to enable/disable themselves, or
 * otherwise change themselves, <code>AppMenuBar</code> will call their update method. This is why
 * all program actions should inherit <code>UpdatingAction</code> (via <code>IdentifiedSingleAction
 * </code> or <code>IdentifiedMutliAction</code>), as the abstract class takes care of registration
 * automatically for you.
 *
 * <p>See the the behaviors class for more information.
 */
@Singleton
public class AppMenuBar extends JMenuBar {
    private static final Logger logger = LoggerFactory.getLogger(AppMenuBar.class);

    private static AppMenuBar instance;

    private static String annotator;

    private static boolean showPreferencesInMenu;

    private final OpenWordpoolAction openWordpoolAction;
    private final ExitAction exitAction;
    private final EditShortcutsAction editShortcutsAction;
    private final PreferencesAction preferencesAction;
    private final PlayPauseAction playPauseAction;
    private final SeekToStartAction seekToStartAction;
    private final ReplayLast200MillisAction replayLast200MillisAction;
    // private final ReturnToLastPositionAction returnToLastPositionAction;
    // private final ReplayLastPositionAction replayLastPositionAction;
    private final DoneAction doneAction;
    private final AboutAction aboutAction;
    private final TipsMessageAction tipsMessageAction;
    private final VisitTutorialSiteAction visitTutorialSiteAction;
    private final CheckUpdatesAction checkUpdatesAction;
    private final Last200PlusMoveAction last200PlusMoveAction;
    // private final AnnotateRegularAction annotateRegularAction;
    // private final AnnotateIntrusionAction annotateIntrusionAction;
    // private final actions.ToggleAnnotationsAction toggleAnnotationsAction;
    private final core.actions.ZoomInAction zoomInAction;
    private final core.actions.ZoomOutAction zoomOutAction;
    private final core.actions.OpenAudioFileAction openAudioFileAction;
    private final core.actions.OpenAudioFolderAction openAudioFolderAction;
    private final core.actions.SeekActionFactory seekActionFactory;
    private final core.actions.ScreenSeekForwardAction screenSeekForwardAction;
    private final core.actions.ScreenSeekBackwardAction screenSeekBackwardAction;

    /** Creates a new instance of the object, filling the menus and creating the actions. */
    @SuppressWarnings("StaticAssignmentInConstructor")
    @Inject
    public AppMenuBar(
            ui.LookAndFeelManager lookAndFeelManager,
            OpenWordpoolAction openWordpoolAction,
            ExitAction exitAction,
            ui.actions.ActionsManager actionsManager,
            EditShortcutsAction editShortcutsAction,
            PreferencesAction preferencesAction,
            PlayPauseAction playPauseAction,
            SeekToStartAction seekToStartAction,
            ReplayLast200MillisAction replayLast200MillisAction,
            // ReturnToLastPositionAction returnToLastPositionAction,
            // ReplayLastPositionAction replayLastPositionAction,
            DoneAction doneAction,
            AboutAction aboutAction,
            TipsMessageAction tipsMessageAction,
            VisitTutorialSiteAction visitTutorialSiteAction,
            CheckUpdatesAction checkUpdatesAction,
            Last200PlusMoveAction last200PlusMoveAction,
            // AnnotateRegularAction annotateRegularAction,
            // AnnotateIntrusionAction annotateIntrusionAction,
            // actions.ToggleAnnotationsAction toggleAnnotationsAction,
            core.actions.ZoomInAction zoomInAction,
            core.actions.ZoomOutAction zoomOutAction,
            core.actions.OpenAudioFileAction openAudioFileAction,
            core.actions.OpenAudioFolderAction openAudioFolderAction,
            core.actions.SeekActionFactory seekActionFactory,
            core.actions.ScreenSeekForwardAction screenSeekForwardAction,
            core.actions.ScreenSeekBackwardAction screenSeekBackwardAction) {
        this.openWordpoolAction = openWordpoolAction;
        this.exitAction = exitAction;
        this.editShortcutsAction = editShortcutsAction;
        this.preferencesAction = preferencesAction;
        this.playPauseAction = playPauseAction;
        this.seekToStartAction = seekToStartAction;
        this.replayLast200MillisAction = replayLast200MillisAction;
        // this.returnToLastPositionAction = returnToLastPositionAction;
        // this.replayLastPositionAction = replayLastPositionAction;
        this.doneAction = doneAction;
        this.aboutAction = aboutAction;
        this.tipsMessageAction = tipsMessageAction;
        this.visitTutorialSiteAction = visitTutorialSiteAction;
        this.checkUpdatesAction = checkUpdatesAction;
        this.last200PlusMoveAction = last200PlusMoveAction;
        // this.annotateRegularAction = annotateRegularAction;
        // this.annotateIntrusionAction = annotateIntrusionAction;
        // this.toggleAnnotationsAction = toggleAnnotationsAction;
        this.zoomInAction = zoomInAction;
        this.zoomOutAction = zoomOutAction;
        this.openAudioFileAction = openAudioFileAction;
        this.openAudioFolderAction = openAudioFolderAction;
        this.seekActionFactory = seekActionFactory;
        this.screenSeekForwardAction = screenSeekForwardAction;
        this.screenSeekBackwardAction = screenSeekBackwardAction;
        showPreferencesInMenu = lookAndFeelManager.shouldShowPreferencesInMenu();
        initFileMenu();
        initControlsMenu();
        initAnnotationMenu();
        initViewMenu();
        initHelpMenu();

        // Set the singleton instance after full initialization
        instance = this;
    }

    /** Creates the File menu, only adding exit and preferences options for non-OSX platforms. */
    private void initFileMenu() {
        JMenu jmFile = new JMenu("File");
        JMenuItem jmiOpenWordpool = new JMenuItem(new SwingAction(openWordpoolAction));
        jmFile.add(jmiOpenWordpool);

        JMenuItem jmiOpenAudioFile = new JMenuItem(new SwingAction(openAudioFileAction));
        JMenuItem jmiOpenAudioFolder = new JMenuItem(new SwingAction(openAudioFolderAction));
        jmFile.add(jmiOpenAudioFile);
        jmFile.add(jmiOpenAudioFolder);
        JMenuItem jmiShortcuts = new JMenuItem(new SwingAction(editShortcutsAction));
        jmFile.add(jmiShortcuts);
        if (showPreferencesInMenu) {
            jmFile.addSeparator();
            JMenuItem jmiPreferences = new JMenuItem(new SwingAction(preferencesAction));
            jmFile.add(jmiPreferences);
            jmFile.addSeparator();
            JMenuItem jmiExit = new JMenuItem(new SwingAction(exitAction));
            jmFile.add(jmiExit);
        }
        add(jmFile);
    }

    /** Creates the Controls menu which controls audio playback and position. */
    private void initControlsMenu() {
        JMenu jmAudio = new JMenu("Controls");

        // PlayPauseAction is now event-driven, no workaround needed
        JMenuItem jmiPlayPause = new JMenuItem(new SwingAction(playPauseAction));

        JMenuItem jmiSeekToStart = new JMenuItem(new SwingAction(seekToStartAction));
        JMenuItem jmiReplay = new JMenuItem(new SwingAction(replayLast200MillisAction));
        // JMenuItem jmiLastPos = new JMenuItem(returnToLastPositionAction);
        // JMenuItem jmiReplayLast = new JMenuItem(replayLastPositionAction);

        JMenu jmSeek = new JMenu("Seek");

        // Add seek actions using the factory
        JMenuItem jmiSeekForwardSmall =
                new JMenuItem(new SwingAction(seekActionFactory.createSeekAction("forward-small")));
        JMenuItem jmiSeekSmallBackward =
                new JMenuItem(
                        new SwingAction(seekActionFactory.createSeekAction("backward-small")));
        JMenuItem jmiSeekForwardMedium =
                new JMenuItem(
                        new SwingAction(seekActionFactory.createSeekAction("forward-medium")));
        JMenuItem jmiSeekBackwardMedium =
                new JMenuItem(
                        new SwingAction(seekActionFactory.createSeekAction("backward-medium")));
        JMenuItem jmiSeekForwardLarge =
                new JMenuItem(new SwingAction(seekActionFactory.createSeekAction("forward-large")));
        JMenuItem jmiSeekBackwardLarge =
                new JMenuItem(
                        new SwingAction(seekActionFactory.createSeekAction("backward-large")));

        JMenuItem jmiLast200MoveRight = new JMenuItem(new SwingAction(last200PlusMoveAction));
        JMenuItem jmiLast200MoveLeft = new JMenuItem(new SwingAction(last200PlusMoveAction));

        // Add screen seek actions
        JMenuItem jmiScreenForward = new JMenuItem(new SwingAction(screenSeekForwardAction));
        JMenuItem jmiScreenBackward = new JMenuItem(new SwingAction(screenSeekBackwardAction));

        jmSeek.add(jmiSeekForwardSmall);
        jmSeek.add(jmiSeekSmallBackward);
        jmSeek.add(jmiSeekForwardMedium);
        jmSeek.add(jmiSeekBackwardMedium);
        jmSeek.add(jmiSeekForwardLarge);
        jmSeek.add(jmiSeekBackwardLarge);
        jmSeek.add(jmiLast200MoveRight);
        jmSeek.add(jmiLast200MoveLeft);
        jmSeek.add(jmiScreenForward);
        jmSeek.add(jmiScreenBackward);

        jmAudio.add(jmiPlayPause);
        jmAudio.add(jmiSeekToStart);
        jmAudio.add(jmiReplay);
        // jmAudio.add(jmiLastPos);
        // jmAudio.add(jmiReplayLast);
        jmAudio.add(jmSeek);
        add(jmAudio);
    }

    /** Creates the annotation menu. */
    private void initAnnotationMenu() {
        JMenu jmAnnotation = new JMenu("Annotation");
        JMenuItem jmiDone = new JMenuItem(new SwingAction(doneAction));
        jmAnnotation.add(jmiDone);
        // JMenuItem jmiNextAnn = new JMenuItem(toggleAnnotationsAction);
        // jmiNextAnn.setText("Next Annotation");
        // jmAnnotation.add(jmiNextAnn);
        // JMenuItem jmiPrevAnn = new JMenuItem(toggleAnnotationsAction);
        // jmiPrevAnn.setText("Previous Annotation");
        // jmAnnotation.add(jmiPrevAnn);
        add(jmAnnotation);
    }

    /** Creates the View menu, which controls aspects of the waveform's appearance. */
    private void initViewMenu() {
        JMenu jmView = new JMenu("View");
        JMenuItem jmiZoomIn = new JMenuItem(new SwingAction(zoomInAction));
        JMenuItem jmiZoomOut = new JMenuItem(new SwingAction(zoomOutAction));
        jmView.add(jmiZoomIn);
        jmView.add(jmiZoomOut);
        add(jmView);
    }

    /** Creates the help menu, only adding an about menu for non-OSX platforms. */
    private void initHelpMenu() {
        JMenu jmHelp = new JMenu("Help");
        JMenuItem jmiVisitMemLab = new JMenuItem(new SwingAction(visitTutorialSiteAction));
        JMenuItem jmiKeys = new JMenuItem(new SwingAction(tipsMessageAction));
        jmHelp.add(jmiVisitMemLab);
        jmHelp.add(jmiKeys);
        // Manual update check menu item
        JMenuItem jmiCheckUpdates = new JMenuItem(new SwingAction(checkUpdatesAction));
        jmHelp.add(jmiCheckUpdates);
        if (showPreferencesInMenu) {
            jmHelp.addSeparator();
            JMenuItem jmiAbout = new JMenuItem(new SwingAction(aboutAction));
            jmHelp.add(jmiAbout);
        }
        add(jmHelp);
    }

    /**
     * Singleton accessor.
     *
     * @return The singleton <code>AppMenuBar</code>
     */
    public static AppMenuBar getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "AppMenuBar not initialized via DI. Ensure GuiceBootstrap.create() was called"
                            + " first.");
        }
        return instance;
    }

    public static void setAnnotator(String annotator) {
        AppMenuBar.annotator = annotator;
    }

    public static String getAnnotator() {
        return annotator;
    }
}
