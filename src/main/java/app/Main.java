package app;

import app.di.GuiceBootstrap;
import java.util.Arrays;
import javax.swing.SwingUtilities;

/** Entry point of the entire program. */
public class Main {

    /**
     * Program entry point. Only used to create an object of this class running on the event
     * dispatch thread, as per Java Swing policy.
     */
    public static void main(String[] args) {
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
