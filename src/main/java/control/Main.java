package control;

import behaviors.singleact.CheckUpdatesAction;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import components.MyFocusTraversalPolicy;
import components.MyFrame;
import components.MyMenu;
import components.MySplitPane;
import components.ShortcutFrame;
import components.WindowManager;
import info.Constants;
import java.awt.event.ActionEvent;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point class of the entire program. */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static boolean DEV_MODE = false;

    public static final boolean DEBUG_FOCUS = false;

    /**
     * True entry point of program after Swing thread is created.
     *
     * <p>First makes look and feel customizations for Mac OSX and other platforms, and then
     * launches main program window. Creates and runs thread to check for updates.
     */
    private Main() {
        // Configure macOS integration
        components.MacOSIntegration.integrateWithMacOS();
        logger.debug("macOS integration: {}", components.MacOSIntegration.getIntegrationStatus());

        com.formdev.flatlaf.FlatLightLaf.setup();

        var unused = ShortcutFrame.instance.toString(); // initialize
        MyFrame.getInstance(); // creates all the components, so after this line everything is
        // made, just not visible
        MyFrame.getInstance().setFocusTraversalPolicy(new MyFocusTraversalPolicy());
        MyMenu.updateActions(); // set up all action states before frame becomes visible but after
        // all components tied to the actions are made
        new WindowManager().restoreWindowLayout(MyFrame.getInstance(), MySplitPane.getInstance());
        MyFrame.getInstance().setVisible(true);
        if (!DEV_MODE) {
            new CheckUpdatesAction(false)
                    .actionPerformed(
                            new ActionEvent(
                                    MyFrame.getInstance(), ActionEvent.ACTION_PERFORMED, null));
        }
    }

    public static boolean developerMode() {
        return DEV_MODE;
    }

    /**
     * Program entry point. Only used to create an object of this class running on the event
     * dispatch thread, as per Java Swing policy.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        // Also check args passed directly to main (in case not using gradle)
        if (args.length > 0 && args[0].equals("-developer")) {
            DEV_MODE = true;
            // Set debug logging level in developer mode
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.DEBUG);
            logger.info("Developer mode enabled - logging level set to DEBUG");
        }

        logger.debug("Running {} in developer mode", Constants.programName);
        SwingUtilities.invokeLater(
                new Runnable() {
                    @Override
                    public void run() {
                        new Main();
                    }
                });
    }
}
