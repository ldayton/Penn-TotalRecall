package state;

import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.env.Constants;
import core.events.ErrorRequestedEvent;
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
import ui.wordpool.WordpoolDisplay;
import ui.wordpool.WordpoolFileParser;
import ui.wordpool.WordpoolWord;

/** Manages wordpool loading and display. */
@Singleton
public class WordpoolManager {
    private static final Logger logger = LoggerFactory.getLogger(WordpoolManager.class);

    private final EventDispatchBus eventBus;
    private final WordpoolDisplay wordpoolDisplay;
    private final WaveformSessionDataSource sessionDataSource;

    @Inject
    public WordpoolManager(
            @NonNull EventDispatchBus eventBus,
            @NonNull WordpoolDisplay wordpoolDisplay,
            @NonNull WaveformSessionDataSource sessionDataSource) {
        this.eventBus = eventBus;
        this.wordpoolDisplay = wordpoolDisplay;
        this.sessionDataSource = sessionDataSource;
        eventBus.subscribe(this);
    }

    @Subscribe
    public void onWordpoolFileSelected(@NonNull WordpoolFileSelectedEvent event) {
        switchWordpool(event.getFile());
    }

    private void switchWordpool(File file) {
        try {
            List<WordpoolWord> words = WordpoolFileParser.parse(file, false);
            wordpoolDisplay.removeAllWords();
            wordpoolDisplay.addWordpoolWords(words);

            // Check if audio is loaded and look for matching LST file
            if (sessionDataSource.isAudioLoaded()) {
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
        } catch (IOException e) {
            logger.error("Error processing wordpool file", e);
            eventBus.publish(new ErrorRequestedEvent("Cannot process wordpool file!"));
        }
    }
}
