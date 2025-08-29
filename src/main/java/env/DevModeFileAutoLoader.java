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

        // Gather all sample files from packaging/samples and add them to the list
        File samplesDir = new File("packaging/samples");
        File[] allCandidates = samplesDir.exists() ? samplesDir.listFiles() : null;
        if (allCandidates == null || allCandidates.length == 0) {
            logger.warn("No sample files found in: {}", samplesDir.getPath());
            return;
        }

        // Add all supported files in the samples directory to the list
        boolean anyLoaded = AudioFileDisplay.addFilesIfSupported(allCandidates);
        if (!anyLoaded) {
            logger.warn("No supported audio files found to auto-load in development mode");
            return;
        }

        // Switch to sample.wav specifically (waveform ready to play); fallback to first entry
        File sampleFile = new File(SAMPLE_FILE_PATH);
        int targetIndex = 0;
        for (int i = 0; i < audioFileList.getModel().getSize(); i++) {
            var f = audioFileList.getModel().getElementAt(i);
            if (f.getAbsolutePath().equals(sampleFile.getAbsolutePath())) {
                targetIndex = i;
                break;
            }
        }
        if (audioFileList.getModel().getSize() > 0) {
            var loadedFile = audioFileList.getModel().getElementAt(targetIndex);
            audioState.switchFile(loadedFile);
            logger.info(
                    "Development mode: switched to {} ({} files listed)",
                    loadedFile.getName(),
                    audioFileList.getModel().getSize());
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
