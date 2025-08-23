package components;

import actions.AboutAction;
import actions.AnnotateIntrusionAction;
import actions.AnnotateRegularAction;
import actions.DoneAction;
import actions.EditShortcutsAction;
import actions.ExitAction;
import actions.Last200PlusMoveAction;
import actions.OpenWordpoolAction;
import actions.PlayPauseAction;
import actions.PreferencesAction;
import actions.ReplayLast200MillisAction;
import actions.ReplayLastPositionAction;
import actions.ReturnToLastPositionAction;
import actions.StopAction;
import actions.TipsMessageAction;
import actions.VisitTutorialSiteAction;
import behaviors.UpdatingAction;
import behaviors.multiact.OpenAudioLocationAction;
import behaviors.multiact.ScreenSeekAction;
import behaviors.multiact.ToggleAnnotationsAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Program menu bar and trigger for updates in program's state that concern program actions.
 *
 * <p>Most of the program's user actions are tied to menu items in this <code>JMenu</code>.
 * Programmers adding new functionality should in almost all cases add a corresponding <code>
 * JMenuItem</code> here. However, there are those cases where an Action should only be initiated in
 * another way (e.g., hitting enter in a text field), for which a menu item is inappropriate.
 *
 * <p>MyMenu keeps track of all {@link UpdatingAction} instances registered with it. Any time a
 * change occurs in program state that might cause certain actions to enable/disable themselves, or
 * otherwise change themselves, <code>MyMenu</code> will call their update method. This is why all
 * program actions should inherit <code>UpdatingAction</code> (via <code>IdentifiedSingleAction
 * </code> or <code>IdentifiedMutliAction</code>), as the abstract class takes care of registration
 * automatically for you.
 *
 * <p>See the the behaviors class for more information.
 */
@Singleton
public class MyMenu extends JMenuBar {
    private static final Logger logger = LoggerFactory.getLogger(MyMenu.class);

    private static PlayPauseAction workaroundAction;

    private static Set<UpdatingAction> allActions = new HashSet<>();

    private static MyMenu instance;

    private static String annotator;

    private static boolean showPreferencesInMenu;

    private final env.LookAndFeelManager lookAndFeelManager;
    private final OpenWordpoolAction openWordpoolAction;
    private final ExitAction exitAction;
    private final actions.ActionsManager actionsManager;
    private final EditShortcutsAction editShortcutsAction;
    private final PreferencesAction preferencesAction;
    private final PlayPauseAction playPauseAction;
    private final StopAction stopAction;
    private final ReplayLast200MillisAction replayLast200MillisAction;
    private final ReturnToLastPositionAction returnToLastPositionAction;
    private final ReplayLastPositionAction replayLastPositionAction;
    private final DoneAction doneAction;
    private final AboutAction aboutAction;
    private final TipsMessageAction tipsMessageAction;
    private final VisitTutorialSiteAction visitTutorialSiteAction;
    private final Last200PlusMoveAction last200PlusMoveAction;
    private final AnnotateRegularAction annotateRegularAction;
    private final AnnotateIntrusionAction annotateIntrusionAction;

    /** Creates a new instance of the object, filling the menus and creating the actions. */
    @SuppressWarnings("StaticAssignmentInConstructor")
    @Inject
    public MyMenu(
            env.LookAndFeelManager lookAndFeelManager,
            OpenWordpoolAction openWordpoolAction,
            ExitAction exitAction,
            actions.ActionsManager actionsManager,
            EditShortcutsAction editShortcutsAction,
            PreferencesAction preferencesAction,
            PlayPauseAction playPauseAction,
            StopAction stopAction,
            ReplayLast200MillisAction replayLast200MillisAction,
            ReturnToLastPositionAction returnToLastPositionAction,
            ReplayLastPositionAction replayLastPositionAction,
            DoneAction doneAction,
            AboutAction aboutAction,
            TipsMessageAction tipsMessageAction,
            VisitTutorialSiteAction visitTutorialSiteAction,
            Last200PlusMoveAction last200PlusMoveAction,
            AnnotateRegularAction annotateRegularAction,
            AnnotateIntrusionAction annotateIntrusionAction) {
        this.lookAndFeelManager = lookAndFeelManager;
        this.openWordpoolAction = openWordpoolAction;
        this.exitAction = exitAction;
        this.actionsManager = actionsManager;
        this.editShortcutsAction = editShortcutsAction;
        this.preferencesAction = preferencesAction;
        this.playPauseAction = playPauseAction;
        this.stopAction = stopAction;
        this.replayLast200MillisAction = replayLast200MillisAction;
        this.returnToLastPositionAction = returnToLastPositionAction;
        this.replayLastPositionAction = replayLastPositionAction;
        this.doneAction = doneAction;
        this.aboutAction = aboutAction;
        this.tipsMessageAction = tipsMessageAction;
        this.visitTutorialSiteAction = visitTutorialSiteAction;
        this.last200PlusMoveAction = last200PlusMoveAction;
        this.annotateRegularAction = annotateRegularAction;
        this.annotateIntrusionAction = annotateIntrusionAction;
        showPreferencesInMenu = lookAndFeelManager.shouldShowPreferencesInMenu();
        initFileMenu();
        initControlsMenu();
        initAnnotationMenu();
        //		initViewMenu();
        initHelpMenu();

        // Set the singleton instance after full initialization
        instance = this;
    }

    /** Creates the File menu, only adding exit and preferences options for non-OSX platforms. */
    private void initFileMenu() {
        JMenu jmFile = new JMenu("File");
        JMenuItem jmiOpenWordpool = new JMenuItem(openWordpoolAction);
        if (lookAndFeelManager.shouldUseAWTFileChoosers()) {
            OpenAudioLocationAction openFileAction =
                    new OpenAudioLocationAction(OpenAudioLocationAction.SelectionMode.FILES_ONLY);
            JMenuItem jmiOpenAudioFile = new JMenuItem(openFileAction);
            OpenAudioLocationAction openFolderAction =
                    new OpenAudioLocationAction(
                            OpenAudioLocationAction.SelectionMode.DIRECTORIES_ONLY);
            JMenuItem jmiOpenAudioFolder = new JMenuItem(openFolderAction);
            jmFile.add(jmiOpenAudioFile);
            jmFile.add(jmiOpenAudioFolder);
        } else {
            JMenuItem jmiOpenAudio =
                    new JMenuItem(
                            new OpenAudioLocationAction(
                                    OpenAudioLocationAction.SelectionMode.FILES_AND_DIRECTORIES));
            jmFile.add(jmiOpenAudio);
        }
        jmFile.add(jmiOpenWordpool);
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

        // see PlayPauseAction docs for explanation of this funniness
        JMenuItem jmiPlayPause = new JMenuItem(playPauseAction);
        workaroundAction = playPauseAction;
        jmiPlayPause.addActionListener(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        workaroundAction.actionPerformed(e);
                    }
                });

        JMenuItem jmiStop = new JMenuItem(stopAction);
        JMenuItem jmiReplay = new JMenuItem(replayLast200MillisAction);
        JMenuItem jmiLastPos = new JMenuItem(returnToLastPositionAction);
        JMenuItem jmiReplayLast = new JMenuItem(replayLastPositionAction);

        JMenu jmSeek = new JMenu("Seek");
        behaviors.multiact.SeekAction seekSmallForward =
                new behaviors.multiact.SeekAction(
                        behaviors.multiact.SeekAction.SeekAmount.FORWARD_SMALL);
        JMenuItem jmiSeekForwardSmall = new JMenuItem(seekSmallForward);
        behaviors.multiact.SeekAction seekSmallBackward =
                new behaviors.multiact.SeekAction(
                        behaviors.multiact.SeekAction.SeekAmount.BACKWARD_SMALL);
        JMenuItem jmiSeekSmallBackward = new JMenuItem(seekSmallBackward);
        behaviors.multiact.SeekAction seekMediumForward =
                new behaviors.multiact.SeekAction(
                        behaviors.multiact.SeekAction.SeekAmount.FORWARD_MEDIUM);
        JMenuItem jmiSeekForwardMedium = new JMenuItem(seekMediumForward);
        behaviors.multiact.SeekAction seekMediumBackward =
                new behaviors.multiact.SeekAction(
                        behaviors.multiact.SeekAction.SeekAmount.BACKWARD_MEDIUM);
        JMenuItem jmiSeekBackwardMedium = new JMenuItem(seekMediumBackward);
        behaviors.multiact.SeekAction seekLargeForward =
                new behaviors.multiact.SeekAction(
                        behaviors.multiact.SeekAction.SeekAmount.FORWARD_LARGE);
        JMenuItem jmiSeekForwardLarge = new JMenuItem(seekLargeForward);
        behaviors.multiact.SeekAction seekLargeBackward =
                new behaviors.multiact.SeekAction(
                        behaviors.multiact.SeekAction.SeekAmount.BACKWARD_LARGE);
        JMenuItem jmiSeekBackwardLarge = new JMenuItem(seekLargeBackward);
        JMenuItem jmiLast200MoveRight = new JMenuItem(last200PlusMoveAction);
        JMenuItem jmiLast200MoveLeft = new JMenuItem(last200PlusMoveAction);
        JMenuItem jmiScreenForward =
                new JMenuItem(new ScreenSeekAction(ScreenSeekAction.Dir.FORWARD));
        JMenuItem jmiScreenBackward =
                new JMenuItem(new ScreenSeekAction(ScreenSeekAction.Dir.BACKWARD));
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
        jmAudio.add(jmiLastPos);
        jmAudio.add(jmiReplayLast);
        jmAudio.add(jmSeek);
        add(jmAudio);
    }

    /** Creates the annotation menu. */
    private void initAnnotationMenu() {
        JMenu jmAnnotation = new JMenu("Annotation");
        JMenuItem jmiDone = new JMenuItem(doneAction);
        jmAnnotation.add(jmiDone);
        JMenuItem jmiNextAnn =
                new JMenuItem(
                        new ToggleAnnotationsAction(ToggleAnnotationsAction.Direction.FORWARD));
        jmAnnotation.add(jmiNextAnn);
        JMenuItem jmiPrevAnn =
                new JMenuItem(
                        new ToggleAnnotationsAction(ToggleAnnotationsAction.Direction.BACKWARD));
        jmAnnotation.add(jmiPrevAnn);
        add(jmAnnotation);
    }

    /** Creates the View menu, which controls aspects of the waveform's appearance. */
    @SuppressWarnings("unused")
    private void initViewMenu() {
        JMenu jmView = new JMenu("View");
        JMenuItem jmiZoomIn =
                new JMenuItem(
                        new behaviors.multiact.ZoomAction(
                                behaviors.multiact.ZoomAction.Direction.IN));
        JMenuItem jmiZoomOut =
                new JMenuItem(
                        new behaviors.multiact.ZoomAction(
                                behaviors.multiact.ZoomAction.Direction.OUT));
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
        if (showPreferencesInMenu) {
            jmHelp.addSeparator();
            JMenuItem jmiAbout = new JMenuItem(aboutAction);
            jmHelp.add(jmiAbout);
        }
        add(jmHelp);
    }

    /**
     * Trigger for updating all actions.
     *
     * <p>Should be called anytime program state changes in a way that may interest the registered
     * actions. Simple calls {@link UpdatingAction#update()} on every registered action.
     */
    public static void updateActions() {
        for (Object ia : allActions.toArray()) {
            ((UpdatingAction) ia).update();
        }

        // Also update ADI actions that extend BaseAction
        if (instance != null) {
            instance.doneAction.update();
            instance.exitAction.update();
            instance.playPauseAction.update();
            instance.stopAction.update();
            instance.replayLast200MillisAction.update();
            instance.returnToLastPositionAction.update();
            instance.replayLastPositionAction.update();
            instance.aboutAction.update();
            instance.preferencesAction.update();
            instance.editShortcutsAction.update();
            instance.tipsMessageAction.update();
            instance.visitTutorialSiteAction.update();
            instance.last200PlusMoveAction.update();
            instance.openWordpoolAction.update();
            instance.annotateRegularAction.update();
            instance.annotateIntrusionAction.update();
        }
    }

    public static void updateSeekActions() {
        for (UpdatingAction ia : allActions) {
            if (ia instanceof behaviors.multiact.SeekAction seekAction) {
                seekAction.updateSeekAmount();
            }
            // Note: Last200PlusMoveForwardAction and Last200PlusMoveBackwardAction are ADI actions
            // that handle their own updates and don't extend UpdatingAction
        }
    }

    /**
     * Registere the provided <code>UpdatingAction</code> to receive updates when program state
     * changes.
     *
     * <p>This method is normally called automatically by the <code>UpdatingAction</code>
     * constructor. Actions that have already been added will not be added a second time.
     *
     * @param act The <code>UpdatingAction</code> that wishes to recieve updates calls
     */
    public static void registerAction(UpdatingAction act) {
        if (allActions.add(act) == false) {
            logger.warn("double registration of: " + act);
        }
    }

    /**
     * Registers all UpdatingAction instances with the ActionsManager. This method is called during
     * initialization after all actions have been created.
     */
    public static void registerAllActionsWithManager() {
        if (instance == null) {
            throw new IllegalStateException("MyMenu not initialized via DI");
        }
        for (UpdatingAction action : allActions) {
            instance.actionsManager.registerUpdatingAction(action);
        }
    }

    /**
     * Singleton accessor.
     *
     * @return The singleton <code>MyMenu</code>
     */
    public static MyMenu getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "MyMenu not initialized via DI. Ensure GuiceBootstrap.create() was called"
                            + " first.");
        }
        return instance;
    }

    public static void setAnnotator(String annotator) {
        MyMenu.annotator = annotator;
    }

    public static String getAnnotator() {
        return annotator;
    }
}
