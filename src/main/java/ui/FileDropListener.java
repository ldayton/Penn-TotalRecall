package ui;

import core.dispatch.EventDispatchBus;
import core.env.Constants;
import core.events.WordpoolFileSelectedEvent;
import core.state.WaveformSessionDataSource;
import core.util.OsPath;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.audiofiles.AudioFileDisplay;
import ui.wordpool.WordpoolDisplay;
import ui.wordpool.WordpoolFileParser;

/**
 * A <code>FileDrop.Listener</code> that catches directories and folders dropped on <code>MainFrame
 * </code>, adding the appropriate files to the <code>AudioFileDisplay</code>.
 */
@Singleton
public class FileDropListener implements FileDrop.Listener {
    private static final Logger logger = LoggerFactory.getLogger(FileDropListener.class);

    private final EventDispatchBus eventBus;
    private final WaveformSessionDataSource sessionDataSource;
    private final WordpoolDisplay wordpoolDisplay;

    @Inject
    public FileDropListener(
            EventDispatchBus eventBus,
            WaveformSessionDataSource sessionDataSource,
            WordpoolDisplay wordpoolDisplay) {
        this.eventBus = eventBus;
        this.sessionDataSource = sessionDataSource;
        this.wordpoolDisplay = wordpoolDisplay;
    }

    /**
     * Handles drag and drop of audio files or a wordpool document.
     *
     * <p>For each directory dropped, adds the directory's normal files to the file batch. Adds each
     * file dropped to the batch. Finally, adds the whole batch to the <code>AudioFileDisplay</code>
     * . using {@link AudioFileDisplay#addFilesIfSupported(File[])}.
     *
     * <p>Files are added in a batch, instead of one at a time, to the <code>AudioFileDisplay</code>
     * in keeping with that classes policies on sorting optimization.
     *
     * <p>Note that all files described above are given to the <code>AudioFileDisplay</code>, which
     * sorts out which ones are actually the correct format, etc.
     *
     * @param files The <code>Files</code> that were dropped
     * @param evt The <code>DropTargetDropEvent</code> provided by the trigger
     */
    public void filesDropped(File[] files, DropTargetDropEvent evt) {
        boolean somethingAccepted = false;
        if (files.length > 0) {
            boolean wordpoolFound = false;
            if (files.length == 1) { // check for wordpool file
                if (files[0].getName().toLowerCase().endsWith(Constants.wordpoolFileExtension)) {
                    eventBus.publish(new WordpoolFileSelectedEvent(files[0]));

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
                                                            WordpoolFileParser.parse(
                                                                    lstFile, true));
                                                } catch (IOException e) {
                                                    logger.error(
                                                            "Error parsing wordpool file during"
                                                                    + " drag and drop: "
                                                                    + lstFile.getAbsolutePath(),
                                                            e);
                                                }
                                            }
                                        });
                    }

                    somethingAccepted = true;
                    wordpoolFound = true;
                }
            }
            if (wordpoolFound == false) { // check for audio files
                for (File f : files) {
                    if (f.isFile()) {
                        if (AudioFileDisplay.addFilesIfSupported(new File[] {f})) {
                            somethingAccepted = true;
                        }
                    } else if (f.isDirectory()) {
                        if (AudioFileDisplay.addFilesIfSupported(f.listFiles())) {
                            somethingAccepted = true;
                        }
                    }
                }
            }
        }
        evt.getDropTargetContext().dropComplete(somethingAccepted);
    }
}
