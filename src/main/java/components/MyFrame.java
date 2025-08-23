package components;

import behaviors.singleact.ExitAction;
import components.waveform.MyGlassPane;
import components.wordpool.WordpoolDisplay;
import env.LookAndFeelManager;
import info.GUIConstants;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.KeyEventPostProcessor;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main window of the program.
 *
 * <p>Every component in the frame (at any level of nesting) that can be clicked by the user (i.e.,
 * is not obscured) must handle focus-passing, see {@link MyFocusTraversalPolicy} for details.
 */
@Singleton
public class MyFrame extends JFrame implements KeyEventPostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(MyFrame.class);

    private static MyFrame instance;
    private final LookAndFeelManager lookAndFeelManager;
    private final MySplitPane mySplitPane;
    private final MyGlassPane myGlassPane;
    private final MyMenu myMenu;
    private final ExitAction exitAction;

    @Inject
    public MyFrame(
            LookAndFeelManager lookAndFeelManager,
            MySplitPane mySplitPane,
            MyGlassPane myGlassPane,
            MyMenu myMenu,
            ExitAction exitAction) {
        this.lookAndFeelManager = lookAndFeelManager;
        this.mySplitPane = mySplitPane;
        this.myGlassPane = myGlassPane;
        this.myMenu = myMenu;
        this.exitAction = exitAction;
        setTitle(GUIConstants.defaultFrameTitle);
        setGlassPane(myGlassPane);
        myGlassPane.setVisible(true);
        setJMenuBar(myMenu);

        // force handling by  WindowListener below
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // handle clicking on the "x" mark to close the window
        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        exitAction.actionPerformed(
                                new ActionEvent(
                                        this,
                                        ActionEvent.ACTION_PERFORMED,
                                        null,
                                        System.currentTimeMillis(),
                                        0));
                    }
                });

        setContentPane(mySplitPane);

        // accept drag and drop of directories and files
        new FileDrop(this, new FileDropListener());

        // Set application icon (platform-specific sizing)
        setIconImage(
                Toolkit.getDefaultToolkit()
                        .getImage(MyFrame.class.getResource(lookAndFeelManager.getAppIconPath())));

        // this is default, but double checking because focusability is needed for
        // MyFocusTraversalPolicy to be used
        setFocusable(true);

        // used to pass focus to text field when someone types outside of the field
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventPostProcessor(this);

        //		getRootPane().putClientProperty("apple.awt.brushMetalLook", Boolean.TRUE);

        // Set the singleton instance after full initialization
        instance = this;
    }

    /**
     * Singleton accessor.
     *
     * @return The singleton <code>MyFrame</code>
     */
    public static MyFrame getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "MyFrame not initialized via DI. Ensure GuiceBootstrap.create() was called"
                            + " first.");
        }
        return instance;
    }

    /**
     * This hears absolutely everything while the JVM has focus (includes preferences frame)
     *
     * <p>Currently this class is used to pass focus to the {@link wordpool.WordpoolTextField} when
     * the user starts typing while <code>MyFrame</code> is selected. Also used for focus debugging.
     * If {@link Start#DEBUG_FOCUS} is <code>true</code> the focus owner will be printed every time
     * a key is pressed.
     *
     * <p>Please note that arrow keys do NOT generate key typed events, so they are never consumed
     * Also note key typed events always have the location KeyEvent.KEY_LOCATION_UNKNOWN Because
     * arrow keys don't generate KeyTyped events, InputMaps are never get them. They can be heard
     * with KeyListeners, but those require focus, which is messy.
     *
     * <p>Unfortunately there's really no way to tell if a KeyEvent is a press, release, or type
     * event. We use a heuristic to avoid duplicate event handling.
     */
    @SuppressWarnings("all")
    public boolean postProcessKeyEvent(KeyEvent e) {
        // best attempt to restrict us to key_typed so we don't have duplicate events for
        // key_pressed and key_released
        // unfortunately, there might be press/release events with undefined/unkown codes that this
        // condition will accept
        if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_UNKNOWN
                && e.getKeyCode() == KeyEvent.VK_UNDEFINED) {
            if (e.getModifiersEx() == 0) {
                if (Character.isLetter(e.getKeyChar()) || Character.isDigit(e.getKeyChar())) {
                    if (getFocusOwner()
                            != null) { // this is how we guarantee MyFrame is "focused" and not the
                        // PreferencesFrame. not 100% cross-platform since Solaris
                        // separates the focus notion from window
                        // selection/prominence
                        WordpoolDisplay.switchToFocus(Character.toString(e.getKeyChar()));
                    }
                }
            }
        }
        return false;
    }
}
