package control;

import di.GuiceBootstrap;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.Constants;

/** Entry point of the entire program. */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Program entry point. Only used to create an object of this class running on the event
     * dispatch thread, as per Java Swing policy.
     */
    public static void main(String[] args) {
        logger.debug("Welcome to " + Constants.programName);
        boolean integrationTestMode = Arrays.asList(args).contains("--integration-test");
        if (integrationTestMode) {
            AudioIntegrationMode.exitWithTestResult(AudioIntegrationMode.runTest());
        } else {
            SwingUtilities.invokeLater(
                    () -> {
                        GuiceBootstrap bootstrap = GuiceBootstrap.create();
                        bootstrap.startApplication();
                    });
        }
    }
}
