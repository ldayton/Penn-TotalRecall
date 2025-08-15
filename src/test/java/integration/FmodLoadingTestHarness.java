package integration;

import audio.FmodCore;
import util.AppConfig;
import util.LibraryLoadingMode;

/**
 * Integration test harness for FMOD library loading.
 *
 * <p>This standalone test verifies that FMOD libraries can be loaded successfully in different
 * packaging environments. It is designed to be executed within a packaged .app bundle or other
 * deployment scenarios.
 *
 * <p>The test harness runs independently of the main application and reports results via exit
 * codes:
 *
 * <ul>
 *   <li>Exit code 0: Test passed
 *   <li>Exit code 1: Test failed
 * </ul>
 */
public class FmodLoadingTestHarness {

    /**
     * Main entry point for the FMOD loading integration test.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        try {
            System.out.println("🧪 FMOD Loading Integration Test");
            System.out.println("================================");

            // Display configuration information
            AppConfig config = AppConfig.getInstance();
            LibraryLoadingMode mode = config.getFmodLoadingMode();
            System.out.println("Library loading mode: " + mode);

            // Test FMOD library loading by accessing the static instance
            // This will trigger the library loading process
            FmodCore core = FmodCore.instance;
            boolean loaded = (core != null);
            System.out.println("FMOD Core instance created: " + loaded);

            if (loaded) {
                System.out.println("FMOD library path strategy: " + mode);
                System.out.println("✅ FMOD loading test PASSED");
                System.exit(0);
            } else {
                System.err.println("❌ FMOD loading test FAILED: Core instance is null");
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("❌ FMOD loading test FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
