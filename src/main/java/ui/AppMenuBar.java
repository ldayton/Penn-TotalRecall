package ui;

import actions.AboutAction;
// import actions.AnnotateIntrusionAction;
// import actions.AnnotateRegularAction;
import actions.CheckUpdatesAction;
import actions.DoneAction;
import actions.EditShortcutsAction;
import actions.ExitAction;
import actions.Last200PlusMoveAction;
import actions.OpenWordpoolAction;
import actions.PlayPauseAction;
import actions.PreferencesAction;
import actions.ReplayLast200MillisAction;
// import actions.ReplayLastPositionAction;
// import actions.ReturnToLastPositionAction;
import actions.TipsMessageAction;
import actions.VisitTutorialSiteAction;
import core.actions.StopAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.swing.SwingAction;

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
    private final StopAction stopAction;
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
    private final actions.ZoomInAction zoomInAction;
    private final actions.ZoomOutAction zoomOutAction;
    private final actions.OpenAudioFileAction openAudioFileAction;
    private final actions.OpenAudioFolderAction openAudioFolderAction;
    private final actions.SeekAction seekAction;
    private final actions.ScreenSeekForwardAction screenSeekForwardAction;
    private final actions.ScreenSeekBackwardAction screenSeekBackwardAction;

    /** Creates a new instance of the object, filling the menus and creating the actions. */
    @SuppressWarnings("StaticAssignmentInConstructor")
    @Inject
    public AppMenuBar(
            env.LookAndFeelManager lookAndFeelManager,
            OpenWordpoolAction openWordpoolAction,
            ExitAction exitAction,
            actions.ActionsManager actionsManager,
            EditShortcutsAction editShortcutsAction,
            PreferencesAction preferencesAction,
            PlayPauseAction playPauseAction,
            StopAction stopAction,
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
            actions.ZoomInAction zoomInAction,
            actions.ZoomOutAction zoomOutAction,
            actions.OpenAudioFileAction openAudioFileAction,
            actions.OpenAudioFolderAction openAudioFolderAction,
            actions.SeekAction seekAction,
            actions.ScreenSeekForwardAction screenSeekForwardAction,
            actions.ScreenSeekBackwardAction screenSeekBackwardAction) {
        this.openWordpoolAction = openWordpoolAction;
        this.exitAction = exitAction;
        this.editShortcutsAction = editShortcutsAction;
        this.preferencesAction = preferencesAction;
        this.playPauseAction = playPauseAction;
        this.stopAction = stopAction;
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
        this.seekAction = seekAction;
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
        JMenuItem jmiOpenWordpool = new JMenuItem(openWordpoolAction);
        jmFile.add(jmiOpenWordpool);

        JMenuItem jmiOpenAudioFile = new JMenuItem(openAudioFileAction);
        JMenuItem jmiOpenAudioFolder = new JMenuItem(openAudioFolderAction);
        jmFile.add(jmiOpenAudioFile);
        jmFile.add(jmiOpenAudioFolder);
        JMenuItem jmiShortcuts = new JMenuItem(editShortcutsAction);
        jmFile.add(jmiShortcuts);
        if (showPreferencesInMenu) {
            jmFile.addSeparator();
            JMenuItem jmiPreferences = new JMenuItem(preferencesAction);
            jmFile.add(jmiPreferences);
            jmFile.addSeparator();
            JMenuItem jmiExit = new JMenuItem(exitAction);
            jmFile.add(jmiExit);
        }
        add(jmFile);
    }

    /** Creates the Controls menu which controls audio playback and position. */
    private void initControlsMenu() {
        JMenu jmAudio = new JMenu("Controls");

        // PlayPauseAction is now event-driven, no workaround needed
        JMenuItem jmiPlayPause = new JMenuItem(playPauseAction);

        JMenuItem jmiStop = new JMenuItem(new SwingAction(stopAction));
        JMenuItem jmiReplay = new JMenuItem(replayLast200MillisAction);
        // JMenuItem jmiLastPos = new JMenuItem(returnToLastPositionAction);
        // JMenuItem jmiReplayLast = new JMenuItem(replayLastPositionAction);

        JMenu jmSeek = new JMenu("Seek");

        // Add seek actions using the unified ADI action
        JMenuItem jmiSeekForwardSmall = new JMenuItem(seekAction);
        jmiSeekForwardSmall.setText("Forward Small");
        JMenuItem jmiSeekSmallBackward = new JMenuItem(seekAction);
        jmiSeekSmallBackward.setText("Backward Small");
        JMenuItem jmiSeekForwardMedium = new JMenuItem(seekAction);
        jmiSeekForwardMedium.setText("Forward Medium");
        JMenuItem jmiSeekBackwardMedium = new JMenuItem(seekAction);
        jmiSeekBackwardMedium.setText("Backward Medium");
        JMenuItem jmiSeekForwardLarge = new JMenuItem(seekAction);
        jmiSeekForwardLarge.setText("Forward Large");
        JMenuItem jmiSeekBackwardLarge = new JMenuItem(seekAction);
        jmiSeekBackwardLarge.setText("Backward Large");

        JMenuItem jmiLast200MoveRight = new JMenuItem(last200PlusMoveAction);
        JMenuItem jmiLast200MoveLeft = new JMenuItem(last200PlusMoveAction);

        // Add screen seek actions
        JMenuItem jmiScreenForward = new JMenuItem(screenSeekForwardAction);
        JMenuItem jmiScreenBackward = new JMenuItem(screenSeekBackwardAction);

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
        jmAudio.add(jmiStop);
        jmAudio.add(jmiReplay);
        // jmAudio.add(jmiLastPos);
        // jmAudio.add(jmiReplayLast);
        jmAudio.add(jmSeek);
        add(jmAudio);
    }

    /** Creates the annotation menu. */
    private void initAnnotationMenu() {
        JMenu jmAnnotation = new JMenu("Annotation");
        JMenuItem jmiDone = new JMenuItem(doneAction);
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
        JMenuItem jmiZoomIn = new JMenuItem(zoomInAction);
        JMenuItem jmiZoomOut = new JMenuItem(zoomOutAction);
        jmView.add(jmiZoomIn);
        jmView.add(jmiZoomOut);
        add(jmView);
    }

    /** Creates the help menu, only adding an about menu for non-OSX platforms. */
    private void initHelpMenu() {
        JMenu jmHelp = new JMenu("Help");
        JMenuItem jmiVisitMemLab = new JMenuItem(visitTutorialSiteAction);
        JMenuItem jmiKeys = new JMenuItem(tipsMessageAction);
        jmHelp.add(jmiVisitMemLab);
        jmHelp.add(jmiKeys);
        // Manual update check menu item
        JMenuItem jmiCheckUpdates = new JMenuItem(checkUpdatesAction);
        jmHelp.add(jmiCheckUpdates);
        if (showPreferencesInMenu) {
            jmHelp.addSeparator();
            JMenuItem jmiAbout = new JMenuItem(aboutAction);
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
