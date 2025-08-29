package env;

import actions.OpenWordpoolAction;
import components.audiofiles.AudioFileDisplay;
import components.audiofiles.AudioFileList;
import events.ApplicationStartedEvent;
import events.EventDispatchBus;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import state.AudioState;

/**
 * Automatically loads sample audio and wordpool files when running in development mode.
 *
 * <p>This service subscribes to ApplicationStartedEvent and loads packaging/samples/sample.wav and
 * packaging/samples/wordpool.txt if the application is running in development mode
 * (audio.loading.mode=unpackaged) and no files are currently loaded.
 */
@Singleton
public class DevModeFileAutoLoader {
    private static final Logger logger = LoggerFactory.getLogger(DevModeFileAutoLoader.class);
    private static final String SAMPLE_FILE_PATH = "packaging/samples/sample.wav";
    private static final String SAMPLE_WORDPOOL_PATH = "packaging/samples/wordpool.txt";

    private final AppConfig appConfig;
    private final AudioFileList audioFileList;
    private final AudioState audioState;
    private final OpenWordpoolAction openWordpoolAction;

    @Inject
    public DevModeFileAutoLoader(
            AppConfig appConfig,
            AudioFileList audioFileList,
            AudioState audioState,
            OpenWordpoolAction openWordpoolAction,
            EventDispatchBus eventBus) {
        this.appConfig = appConfig;
        this.audioFileList = audioFileList;
        this.audioState = audioState;
        this.openWordpoolAction = openWordpoolAction;
        eventBus.subscribe(this);
    }

    @Subscribe
    public void onApplicationStarted(ApplicationStartedEvent event) {
        // Only run during explicit dev runs (./gradlew runDev), not during tests
        boolean runDevFlag = Boolean.parseBoolean(System.getProperty("app.run.dev", "false"));
        if (!runDevFlag) {
            logger.debug("Not runDev execution (app.run.dev=false), skipping auto-load");
            return;
        }

        // Check if we're in development mode (unpackaged audio loading)
        String loadingMode = appConfig.getProperty("audio.loading.mode", "packaged");
        if (!"unpackaged".equals(loadingMode)) {
            logger.debug(
                    "Not in development mode (audio.loading.mode={}), skipping auto-load",
                    loadingMode);
            return;
        }

        // Check if files are already loaded
        if (audioFileList.getModel().getSize() > 0) {
            logger.debug("Audio files already loaded, skipping auto-load");
            return;
        }

        // Check if sample file exists
        File sampleFile = new File(SAMPLE_FILE_PATH);
        if (!sampleFile.exists()) {
            logger.warn("Sample file does not exist: {}", SAMPLE_FILE_PATH);
            return;
        }

        // Load the sample file
        logger.info("Development mode: auto-loading sample file {}", SAMPLE_FILE_PATH);
        boolean loaded = AudioFileDisplay.addFilesIfSupported(new File[] {sampleFile});

        if (loaded) {
            logger.info("Successfully auto-loaded sample file in development mode");

            // Switch to the loaded file (should be the first and only file in the list)
            if (audioFileList.getModel().getSize() > 0) {
                var loadedFile = audioFileList.getModel().getElementAt(0);
                audioState.switchFile(loadedFile);
                logger.info("Successfully switched to auto-loaded sample file");
            }
        } else {
            logger.warn("Failed to auto-load sample file: {}", SAMPLE_FILE_PATH);
        }

        // Load sample wordpool file
        File sampleWordpoolFile = new File(SAMPLE_WORDPOOL_PATH);
        if (sampleWordpoolFile.exists()) {
            logger.info(
                    "Development mode: auto-loading sample wordpool file {}", SAMPLE_WORDPOOL_PATH);
            openWordpoolAction.switchWordpool(sampleWordpoolFile);
            logger.info("Successfully auto-loaded sample wordpool file");
        } else {
            logger.warn("Sample wordpool file does not exist: {}", SAMPLE_WORDPOOL_PATH);
        }
    }
}
