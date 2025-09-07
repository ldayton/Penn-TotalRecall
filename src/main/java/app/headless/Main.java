package app.headless;

import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Entry point for the headless application. */
public class Main {

    /**
     * Program entry point for headless operation. Creates an event dispatch thread for processing
     * events, while the main thread waits for user input to exit.
     */
    public static void main(String[] args) {
        // Create a single-threaded executor to act as our event dispatch thread
        ExecutorService eventDispatchThread =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r);
                            t.setName("Headless-Event-Dispatch");
                            t.setDaemon(false);
                            return t;
                        });

        // Initialize the application on the event dispatch thread
        eventDispatchThread.execute(
                () -> {
                    HeadlessApp app = HeadlessApp.create();
                    app.startApplication();
                });

        // Main thread waits for input to exit
        System.out.println("Headless application running. Press Enter to exit...");
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();

        // Shutdown the event dispatch thread
        eventDispatchThread.shutdown();
        System.out.println("Headless application stopped.");
    }
}
