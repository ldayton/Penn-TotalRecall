package control;

import di.GuiceBootstrap;
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
            // Normal GUI mode with Guice DI
            SwingUtilities.invokeLater(
                    () -> {
                        GuiceBootstrap bootstrap = GuiceBootstrap.create();
                        bootstrap.startApplication();
                    });
        }
    }
}
