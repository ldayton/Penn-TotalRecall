package control;

import components.MyFocusTraversalPolicy;
import components.MyFrame;
import components.MyMenu;
import components.MySplitPane;
import components.ShortcutFrame;
import components.WindowManager;
import env.Environment;
import info.Constants;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point class of the entire program. */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static final boolean DEBUG_FOCUS = false;

    /**
     * True entry point of program after Swing thread is created.
     *
     * <p>First makes look and feel customizations for Mac OSX and other platforms, and then
     * launches main program window. Creates and runs thread to check for updates.
     */
    private Main() {
        // Initialize environment and configure Look & Feel
        Environment env = Environment.getInstance();
        env.initializeLookAndFeel();

        var unused = ShortcutFrame.instance.toString(); // initialize
        MyFrame.getInstance(); // creates all the components, so after this line everything is
        // made, just not visible
        MyFrame.getInstance().setFocusTraversalPolicy(new MyFocusTraversalPolicy());
        MyMenu.updateActions(); // set up all action states before frame becomes visible but after
        // all components tied to the actions are made
        new WindowManager().restoreWindowLayout(MyFrame.getInstance(), MySplitPane.getInstance());
        MyFrame.getInstance().setVisible(true);
    }

    /**
     * Program entry point. Only used to create an object of this class running on the event
     * dispatch thread, as per Java Swing policy.
     */
    public static void main(String[] args) {
        logger.debug("Running {}", Constants.programName);
        SwingUtilities.invokeLater(
                new Runnable() {
                    @Override
                    public void run() {
                        new Main();
                    }
                });
    }
}
