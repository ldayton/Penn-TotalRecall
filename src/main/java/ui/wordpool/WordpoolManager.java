package ui.wordpool;

import core.audio.session.AudioSessionDataSource;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.env.Constants;
import core.events.AppStateChangedEvent;
import core.events.DialogEvent;
import core.events.WordpoolFileSelectedEvent;
import core.util.OsPath;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.List;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages wordpool loading and display. */
@Singleton
public class WordpoolManager {
    private static final Logger logger = LoggerFactory.getLogger(WordpoolManager.class);

    private final EventDispatchBus eventBus;
    private final WordpoolDisplay wordpoolDisplay;
    private final AudioSessionDataSource sessionDataSource;

    @Inject
    public WordpoolManager(
            @NonNull EventDispatchBus eventBus,
            @NonNull WordpoolDisplay wordpoolDisplay,
            @NonNull AudioSessionDataSource sessionDataSource) {
        this.eventBus = eventBus;
        this.wordpoolDisplay = wordpoolDisplay;
        this.sessionDataSource = sessionDataSource;
        eventBus.subscribe(this);
    }

    @Subscribe
    public void onWordpoolFileSelected(@NonNull WordpoolFileSelectedEvent event) {
        switchWordpool(event.file());
    }

    private void switchWordpool(File file) {
        try {
            List<WordpoolWord> words = WordpoolFileParser.parse(file, false);
            wordpoolDisplay.removeAllWords();
            wordpoolDisplay.addWordpoolWords(words);
            // Attempt to apply LST markings based on current audio (order-independent)
            applyLstForCurrentAudioIfPossible();
        } catch (IOException e) {
            logger.error("Error processing wordpool file", e);
            eventBus.publish(
                    new DialogEvent("Cannot process wordpool file!", DialogEvent.Type.ERROR));
        }
    }

    /**
     * If audio is loaded, attempts to locate and apply the matching LST file for the current audio
     * by marking words in the wordpool display. Safe to call repeatedly.
     */
    private void applyLstForCurrentAudioIfPossible() {
        var snap = sessionDataSource.snapshot();
        if (snap.state() == core.audio.session.AudioSessionStateMachine.State.NO_AUDIO
                || snap.state() == core.audio.session.AudioSessionStateMachine.State.LOADING
                || snap.state() == core.audio.session.AudioSessionStateMachine.State.ERROR) {
            return;
        }
        sessionDataSource
                .getCurrentAudioFilePath()
                .ifPresent(
                        audioPath -> {
                            File lstFile =
                                    new File(
                                            OsPath.basename(audioPath)
                                                    + "."
                                                    + Constants.lstFileExtension);
                            if (lstFile.exists()) {
                                try {
                                    wordpoolDisplay.distinguishAsLst(
                                            WordpoolFileParser.parse(lstFile, true));
                                } catch (IOException e) {
                                    logger.error(
                                            "Failed to parse LST file: "
                                                    + lstFile.getAbsolutePath(),
                                            e);
                                }
                            }
                        });
    }

    /**
     * When app state changes (e.g., audio loaded and transitions to READY/PLAYING), re-apply LST
     * markings if possible. This makes LST application order-independent.
     */
    @Subscribe
    public void onAppStateChanged(@NonNull AppStateChangedEvent event) {
        applyLstForCurrentAudioIfPossible();
    }
}
