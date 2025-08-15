package components;

import behaviors.UpdatingAction;
import behaviors.multiact.Last200PlusMoveAction;
import behaviors.multiact.OpenAudioLocationAction;
import behaviors.multiact.ScreenSeekAction;
import behaviors.multiact.SeekAction;
import behaviors.multiact.ToggleAnnotationsAction;
import behaviors.multiact.ZoomAction;
import behaviors.singleact.AboutAction;
import behaviors.singleact.DoneAction;
import behaviors.singleact.EditShortcutsAction;
import behaviors.singleact.ExitAction;
import behaviors.singleact.OpenWordpoolAction;
import behaviors.singleact.PlayPauseAction;
import behaviors.singleact.PreferencesAction;
import behaviors.singleact.ReplayLast200MillisAction;
import behaviors.singleact.ReplayLastPositionAction;
import behaviors.singleact.ReturnToLastPositionAction;
import behaviors.singleact.StopAction;
import behaviors.singleact.TipsMessageAction;
import behaviors.singleact.VisitTutorialSiteAction;
import info.SysInfo;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.UIManager;

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
public class MyMenu extends JMenuBar {

    private static PlayPauseAction workaroundAction;

    private static Set<UpdatingAction> allActions;

    private static MyMenu instance;

    private static String annotator;

    private static boolean macLF;

    /** Creates a new instance of the object, filling the menus and creating the actions. */
    @SuppressWarnings("StaticAssignmentInConstructor")
    private MyMenu() {
        allActions = new HashSet<>();
        macLF =
                SysInfo.sys.isMacOSX
                        && UIManager.getLookAndFeel()
                                .getClass()
                                .getName()
                                .equals(UIManager.getSystemLookAndFeelClassName());
        initFileMenu();
        initControlsMenu();
        initAnnotationMenu();
        //		initViewMenu();
        initHelpMenu();
    }

    /** Creates the File menu, only adding exit and preferences options for non-OSX platforms. */
    private void initFileMenu() {
        JMenu jmFile = new JMenu("File");
        JMenuItem jmiOpenWordpool = new JMenuItem(new OpenWordpoolAction());
        if (SysInfo.sys.useAWTFileChoosers) {
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
        JMenuItem jmiShortcuts = new JMenuItem(new EditShortcutsAction());
        jmFile.add(jmiShortcuts);
        if (macLF == false) {
            jmFile.addSeparator();
            JMenuItem jmiPreferences = new JMenuItem(new PreferencesAction());
            jmFile.add(jmiPreferences);
            jmFile.addSeparator();
            JMenuItem jmiExit = new JMenuItem(new ExitAction());
            jmFile.add(jmiExit);
        }
        add(jmFile);
    }

    /** Creates the Controls menu which controls audio playback and position. */
    private void initControlsMenu() {
        JMenu jmAudio = new JMenu("Controls");

        // see PlayPauseAction docs for explanation of this funniness
        JMenuItem jmiPlayPause = new JMenuItem(new PlayPauseAction(true));
        workaroundAction = new PlayPauseAction(false);
        jmiPlayPause.addActionListener(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        workaroundAction.actionPerformed(e);
                    }
                });

        JMenuItem jmiStop = new JMenuItem(new StopAction());
        JMenuItem jmiReplay = new JMenuItem(new ReplayLast200MillisAction());
        JMenuItem jmiLastPos = new JMenuItem(new ReturnToLastPositionAction());
        JMenuItem jmiReplayLast = new JMenuItem(new ReplayLastPositionAction());

        JMenu jmSeek = new JMenu("Seek");
        SeekAction seekSmallForward = new SeekAction(SeekAction.SeekAmount.FORWARD_SMALL);
        JMenuItem jmiSeekForwardSmall = new JMenuItem(seekSmallForward);
        SeekAction seekSmallBackward = new SeekAction(SeekAction.SeekAmount.BACKWARD_SMALL);
        JMenuItem jmiSeekSmallBackward = new JMenuItem(seekSmallBackward);
        SeekAction seekMediumForward = new SeekAction(SeekAction.SeekAmount.FORWARD_MEDIUM);
        JMenuItem jmiSeekForwardMedium = new JMenuItem(seekMediumForward);
        SeekAction seekMediumBackward = new SeekAction(SeekAction.SeekAmount.BACKWARD_MEDIUM);
        JMenuItem jmiSeekBackwardMedium = new JMenuItem(seekMediumBackward);
        SeekAction seekLargeForward = new SeekAction(SeekAction.SeekAmount.FORWARD_LARGE);
        JMenuItem jmiSeekForwardLarge = new JMenuItem(seekLargeForward);
        SeekAction seekLargeBackward = new SeekAction(SeekAction.SeekAmount.BACKWARD_LARGE);
        JMenuItem jmiSeekBackwardLarge = new JMenuItem(seekLargeBackward);
        JMenuItem jmiLast200MoveRight =
                new JMenuItem(new Last200PlusMoveAction(Last200PlusMoveAction.Direction.FORWARD));
        JMenuItem jmiLast200MoveLeft =
                new JMenuItem(new Last200PlusMoveAction(Last200PlusMoveAction.Direction.BACKWARD));
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
        JMenuItem jmiDone = new JMenuItem(new DoneAction());
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
        JMenuItem jmiZoomIn = new JMenuItem(new ZoomAction(ZoomAction.Direction.IN));
        JMenuItem jmiZoomOut = new JMenuItem(new ZoomAction(ZoomAction.Direction.OUT));
        jmView.add(jmiZoomIn);
        jmView.add(jmiZoomOut);
        add(jmView);
    }

    /** Creates the help menu, only adding an about menu for non-OSX platforms. */
    private void initHelpMenu() {
        JMenu jmHelp = new JMenu("Help");
        JMenuItem jmiVisitMemLab = new JMenuItem(new VisitTutorialSiteAction());
        JMenuItem jmiKeys = new JMenuItem(new TipsMessageAction());
        jmHelp.add(jmiVisitMemLab);
        jmHelp.add(jmiKeys);
        if (macLF == false) {
            jmHelp.addSeparator();
            JMenuItem jmiAbout = new JMenuItem(new AboutAction());
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
    }

    public static void updateSeekActions() {
        for (UpdatingAction ia : allActions) {
            if (ia instanceof SeekAction seekAction) {
                seekAction.updateSeekAmount();
            } else if (ia instanceof Last200PlusMoveAction moveAction) {
                moveAction.updateSeekAmount();
            }
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
            System.err.println("double registration of: " + act);
        }
    }

    /**
     * Singleton accessor.
     *
     * @return The singleton <code>MyMenu</code>
     */
    public static MyMenu getInstance() {
        if (instance == null) {
            instance = new MyMenu();
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
