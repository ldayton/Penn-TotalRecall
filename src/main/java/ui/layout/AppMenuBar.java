package ui.layout;

import core.actions.impl.AboutAction;
// import actions.AnnotateIntrusionAction;
// import actions.AnnotateRegularAction;
import core.actions.impl.CheckUpdatesAction;
import core.actions.impl.DoneAction;
import core.actions.impl.EditShortcutsAction;
import core.actions.impl.ExitAction;
import core.actions.impl.Last200PlusMoveAction;
import core.actions.impl.OpenWordpoolAction;
import core.actions.impl.PlayPauseAction;
import core.actions.impl.PreferencesAction;
import core.actions.impl.ReplayLast200MillisAction;
// import actions.ReplayLastPositionAction;
// import actions.ReturnToLastPositionAction;
import core.actions.impl.SeekToStartAction;
import core.actions.impl.TipsMessageAction;
import core.actions.impl.VisitTutorialSiteAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import lombok.extern.slf4j.Slf4j;
import ui.adapters.SwingActionRegistry;

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
@Slf4j
public class AppMenuBar extends JMenuBar {

    private static String annotator;

    private static boolean showPreferencesInMenu;

    private final SwingActionRegistry swingActions;

    /** Creates a new instance of the object, filling the menus and creating the actions. */
    @Inject
    public AppMenuBar(
            SwingActionRegistry swingActions,
            ui.LookAndFeelManager lookAndFeelManager,
            OpenWordpoolAction openWordpoolAction,
            ExitAction exitAction,
            ui.actions.ActionManager actionsManager,
            EditShortcutsAction editShortcutsAction,
            PreferencesAction preferencesAction,
            PlayPauseAction playPauseAction,
            SeekToStartAction seekToStartAction,
            ReplayLast200MillisAction replayLast200MillisAction,
            DoneAction doneAction,
            AboutAction aboutAction,
            TipsMessageAction tipsMessageAction,
            VisitTutorialSiteAction visitTutorialSiteAction,
            CheckUpdatesAction checkUpdatesAction,
            Last200PlusMoveAction last200PlusMoveAction,
            core.actions.impl.ZoomInAction zoomInAction,
            core.actions.impl.ZoomOutAction zoomOutAction,
            core.actions.impl.OpenAudioFileAction openAudioFileAction,
            core.actions.impl.OpenAudioFolderAction openAudioFolderAction,
            core.actions.impl.SeekForwardSmallAction seekForwardSmallAction,
            core.actions.impl.SeekBackwardSmallAction seekBackwardSmallAction,
            core.actions.impl.SeekForwardMediumAction seekForwardMediumAction,
            core.actions.impl.SeekBackwardMediumAction seekBackwardMediumAction,
            core.actions.impl.SeekForwardLargeAction seekForwardLargeAction,
            core.actions.impl.SeekBackwardLargeAction seekBackwardLargeAction,
            core.actions.impl.ScreenSeekForwardAction screenSeekForwardAction,
            core.actions.impl.ScreenSeekBackwardAction screenSeekBackwardAction) {
        this.swingActions = swingActions;
        showPreferencesInMenu = lookAndFeelManager.shouldShowPreferencesInMenu();
        initFileMenu();
        initControlsMenu();
        initAnnotationMenu();
        initViewMenu();
        initHelpMenu();
    }

    /** Creates the File menu, only adding exit and preferences options for non-OSX platforms. */
    private void initFileMenu() {
        JMenu jmFile = new JMenu("File");
        JMenuItem jmiOpenWordpool = new JMenuItem(swingActions.get(OpenWordpoolAction.class));
        jmFile.add(jmiOpenWordpool);

        JMenuItem jmiOpenAudioFile =
                new JMenuItem(swingActions.get(core.actions.impl.OpenAudioFileAction.class));
        JMenuItem jmiOpenAudioFolder =
                new JMenuItem(swingActions.get(core.actions.impl.OpenAudioFolderAction.class));
        jmFile.add(jmiOpenAudioFile);
        jmFile.add(jmiOpenAudioFolder);
        JMenuItem jmiShortcuts = new JMenuItem(swingActions.get(EditShortcutsAction.class));
        jmFile.add(jmiShortcuts);
        if (showPreferencesInMenu) {
            jmFile.addSeparator();
            JMenuItem jmiPreferences = new JMenuItem(swingActions.get(PreferencesAction.class));
            jmFile.add(jmiPreferences);
            jmFile.addSeparator();
            JMenuItem jmiExit = new JMenuItem(swingActions.get(ExitAction.class));
            jmFile.add(jmiExit);
        }
        add(jmFile);
    }

    /** Creates the Controls menu which controls audio playback and position. */
    private void initControlsMenu() {
        JMenu jmAudio = new JMenu("Controls");

        // PlayPauseAction is now event-driven, no workaround needed
        JMenuItem jmiPlayPause = new JMenuItem(swingActions.get(PlayPauseAction.class));

        JMenuItem jmiSeekToStart = new JMenuItem(swingActions.get(SeekToStartAction.class));
        JMenuItem jmiReplay = new JMenuItem(swingActions.get(ReplayLast200MillisAction.class));
        // JMenuItem jmiLastPos = new JMenuItem(returnToLastPositionAction);
        // JMenuItem jmiReplayLast = new JMenuItem(replayLastPositionAction);

        JMenu jmSeek = new JMenu("Seek");

        // Add seek actions
        JMenuItem jmiSeekForwardSmall =
                new JMenuItem(swingActions.get(core.actions.impl.SeekForwardSmallAction.class));
        JMenuItem jmiSeekSmallBackward =
                new JMenuItem(swingActions.get(core.actions.impl.SeekBackwardSmallAction.class));
        JMenuItem jmiSeekForwardMedium =
                new JMenuItem(swingActions.get(core.actions.impl.SeekForwardMediumAction.class));
        JMenuItem jmiSeekBackwardMedium =
                new JMenuItem(swingActions.get(core.actions.impl.SeekBackwardMediumAction.class));
        JMenuItem jmiSeekForwardLarge =
                new JMenuItem(swingActions.get(core.actions.impl.SeekForwardLargeAction.class));
        JMenuItem jmiSeekBackwardLarge =
                new JMenuItem(swingActions.get(core.actions.impl.SeekBackwardLargeAction.class));

        JMenuItem jmiLast200MoveRight =
                new JMenuItem(swingActions.get(Last200PlusMoveAction.class));
        JMenuItem jmiLast200MoveLeft = new JMenuItem(swingActions.get(Last200PlusMoveAction.class));

        // Add screen seek actions
        JMenuItem jmiScreenForward =
                new JMenuItem(swingActions.get(core.actions.impl.ScreenSeekForwardAction.class));
        JMenuItem jmiScreenBackward =
                new JMenuItem(swingActions.get(core.actions.impl.ScreenSeekBackwardAction.class));

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
        JMenuItem jmiDone = new JMenuItem(swingActions.get(DoneAction.class));
        jmAnnotation.add(jmiDone);
        add(jmAnnotation);
    }

    /** Creates the View menu, which controls aspects of the waveform's appearance. */
    private void initViewMenu() {
        JMenu jmView = new JMenu("View");
        JMenuItem jmiZoomIn = new JMenuItem(swingActions.get(core.actions.impl.ZoomInAction.class));
        JMenuItem jmiZoomOut =
                new JMenuItem(swingActions.get(core.actions.impl.ZoomOutAction.class));
        jmView.add(jmiZoomIn);
        jmView.add(jmiZoomOut);
        add(jmView);
    }

    /** Creates the help menu, only adding an about menu for non-OSX platforms. */
    private void initHelpMenu() {
        JMenu jmHelp = new JMenu("Help");
        JMenuItem jmiVisitMemLab = new JMenuItem(swingActions.get(VisitTutorialSiteAction.class));
        JMenuItem jmiKeys = new JMenuItem(swingActions.get(TipsMessageAction.class));
        jmHelp.add(jmiVisitMemLab);
        jmHelp.add(jmiKeys);
        // Manual update check menu item
        JMenuItem jmiCheckUpdates = new JMenuItem(swingActions.get(CheckUpdatesAction.class));
        jmHelp.add(jmiCheckUpdates);
        if (showPreferencesInMenu) {
            jmHelp.addSeparator();
            JMenuItem jmiAbout = new JMenuItem(swingActions.get(AboutAction.class));
            jmHelp.add(jmiAbout);
        }
        add(jmHelp);
    }

    public static void setAnnotator(String annotator) {
        AppMenuBar.annotator = annotator;
    }

    public static String getAnnotator() {
        return annotator;
    }
}
