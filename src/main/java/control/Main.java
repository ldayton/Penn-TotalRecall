package control;

import components.MyFocusTraversalPolicy;
import components.MyFrame;
import components.MyMenu;
import components.MySplitPane;
import components.WindowManager;
import env.Environment;
import info.Constants;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point of the entire program. */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Program entry point. Only used to create an object of this class running on the event
     * dispatch thread, as per Java Swing policy.
     */
    public static void main(String[] args) {
        logger.debug("Welcome to " + Constants.programName);
        SwingUtilities.invokeLater(
                () -> {
                    var env = new Environment();
                    var windowManager = new WindowManager();
                    env.initializeLookAndFeel();
                    var mainFrame = MyFrame.createInstance(env);
                    mainFrame.setFocusTraversalPolicy(new MyFocusTraversalPolicy());
                    MyMenu.updateActions();
                    windowManager.restoreWindowLayout(mainFrame, MySplitPane.getInstance());
                    mainFrame.setVisible(true);
                });
    }
}
