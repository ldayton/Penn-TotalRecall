package control;

import components.MyFocusTraversalPolicy;
import components.MyFrame;
import components.MyMenu;
import components.MySplitPane;
import components.WindowManager;
import env.AppConfig;
import env.Environment;
import info.Constants;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.UpdateChecker;

/** Entry point of the entire program. */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Program entry point. Only used to create an object of this class running on the event
     * dispatch thread, as per Java Swing policy.
     */
    public static void main(String[] args) {
        logger.debug("Welcome to " + Constants.programName);

        // Check for integration test mode
        boolean integrationTestMode = false;
        for (String arg : args) {
            if ("--integration-test".equals(arg)) {
                integrationTestMode = true;
                break;
            }
        }

        if (integrationTestMode) {
            AudioIntegrationMode.exitWithTestResult(AudioIntegrationMode.runTest());
        } else {
            // Normal GUI mode
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

                        // Check for updates after UI is ready (async, non-blocking)
                        new UpdateChecker(new AppConfig()).checkForUpdateOnStartup();
                    });
        }
    }
}
