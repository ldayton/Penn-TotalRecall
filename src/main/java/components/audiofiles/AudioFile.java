package components.audiofiles;

import env.Constants;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import util.OsPath;

/**
 * A <code>File</code> that represents an audio file for the purpose of representation in the <code>
 * AudioFileDisplay</code>.
 *
 * <p><code>AudioFiles</code> keep track of whether they are done being annotated or not
 * ("completion status"), and provide file system sanity checks that guarantee the <code>AudioFile
 * </code>'s directory does not contain both temporary and final annotation files for this <code>
 * AudioFile</code>. Please note that the audio file, temporary annotation file, and final
 * annotation file must be in the same directory and must share the same filename up to file
 * extension.
 *
 * <p>NOTE: This class does NOT represent the actual audio data. For that, see {@link
 * control.AudioCalculator}.
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. All state modifications are
 * properly synchronized using atomic operations and thread-safe collections. Multiple threads can
 * safely access and modify AudioFile instances concurrently.
 */
public class AudioFile extends File {

    private final ConcurrentHashMap<ChangeListener, Boolean>
            listeners; // thread-safe collection for listeners

    private final AtomicBoolean done; // atomic boolean for completion status

    /**
     * Creates a new <code>AudioFile</code> from the given path.
     *
     * <p>Automatically determines if the file is done being annotated, using the presence sister
     * annotation files in the same directory to judge. An AudioFile is either done or not done, so
     * this constructor enforces the requirement that the new <code>AudioFile</code>'s directory
     * can't contain both temporary and final annotation files.
     *
     * @param pathname The path of the file to be created
     * @throws AudioFilePathException If the new file's directory contains both temporary and final
     *     annotation files
     */
    public AudioFile(String pathname) throws AudioFilePathException {
        super(pathname);
        listeners = new ConcurrentHashMap<>();
        done = new AtomicBoolean(false);
        updateDoneStatus();
    }

    /**
     * Provide a shorter String representation of this object, for the benefit of the graphical
     * display of the object in the <code>AudioFileList</code>.
     *
     * @return The <code>AudioFile</code>'s name, using the inherited {@link
     *     java.io.File#getName()}.
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * Getter for the <code>AudioFile</code>'s completion status.
     *
     * @return <code>true</code> iff this <code>AudioFile</code> is done being annotated.
     */
    public boolean isDone() {
        return done.get();
    }

    /**
     * Determines how <code>AudioFiles</code> are sorted.
     *
     * <p>The following three rules are applied, in order if precedence:
     *
     * <OL>
     *   <LI><code>AudioFiles</code> come before other types of <code>Objects</code>.
     *   <LI><code>AudioFiles</code> that are still incomplete come before those that are already
     *       done.
     *   <LI><code>AudioFiles</code> sort by alphabetical order.
     * </OL>
     */
    @Override
    public int compareTo(File f) {
        if (f instanceof AudioFile == false) {
            return 1;
        } else {
            AudioFile ff = (AudioFile) f;
            if ((ff.isDone() && isDone()) || (ff.isDone() == false && isDone() == false)) {
                return toString().compareTo(ff.toString());
            } else {
                if (isDone()) {
                    return 1;
                } else {
                    return -1;
                }
            }
        }
    }

    /**
     * Finds hash value based on file path.
     *
     * <p>Returns <code>getAbsolutePath().hashCode()</code>.
     */
    @Override
    public int hashCode() {
        return getAbsolutePath().hashCode();
    }

    /**
     * Two <code>AudioFiles</code> are equal if they have the same absolute path.
     *
     * @return <code>true</code> iff <code>o</code> is an <code>AudioFile</code> with the same
     *     absolute path
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof AudioFile audioFile) {
            if (audioFile.getAbsolutePath().equals(getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the <code>done</code> field by finding if a temporary annotation file or a final
     * annotation file is present in the same directory as this <code>File</code>. Informs listeners
     * if the completion status changes.
     *
     * <p>This method is thread-safe and can be called from multiple threads concurrently.
     *
     * @throws AudioFilePathException If both the temporary and final annotation files are present
     */
    public void updateDoneStatus() throws AudioFilePathException {
        boolean savedStatus = done.get();
        boolean updatedStatus = savedStatus;
        boolean annFileExists = false;
        boolean tmpFileExists = false;

        File annFile =
                new File(
                        OsPath.basename(getAbsolutePath())
                                + "."
                                + Constants.completedAnnotationFileExtension);
        File tmpFile =
                new File(
                        OsPath.basename(getAbsolutePath())
                                + "."
                                + Constants.temporaryAnnotationFileExtension);

        if (annFile.exists()) {
            annFileExists = true;
            updatedStatus = true;
        }
        if (tmpFile.exists()) {
            tmpFileExists = true;
            updatedStatus = false;
        }
        if (annFileExists && tmpFileExists) {
            throw new AudioFilePathException(
                    "Both exist, so I don't know if I'm completed or not:\n"
                            + annFile.getPath()
                            + "\n"
                            + tmpFile.getPath());
        }

        // Atomic update - only notify listeners if status actually changed
        boolean statusChanged = done.compareAndSet(savedStatus, updatedStatus);
        if (statusChanged) {
            // Thread-safe iteration over listeners
            for (ChangeListener listener : listeners.keySet()) {
                listener.stateChanged(new ChangeEvent(this));
            }
        }
    }

    /**
     * Adds a <code>ChangeListener</code> to be notified of updates in this <code>AudioFile</code>'s
     * completion status.
     *
     * <p>This method is thread-safe and can be called from multiple threads concurrently.
     *
     * @param listen The <code>ChangeListener</code> to be added.
     */
    public void addChangeListener(ChangeListener listen) {
        listeners.put(listen, Boolean.TRUE);
    }

    /**
     * Removes all the <code>ChangeListeners</code> registered to receive updates from this <code>
     * AudioFile</code>.
     *
     * <p>This method is thread-safe and can be called from multiple threads concurrently.
     */
    public void removeAllChangeListeners() {
        listeners.clear();
    }

    /**
     * Exception thrown when this <code>AudioFile</code>'s directory contains both temporary and
     * final annotation files for this <code>AudioFile</code>.
     */
    public static class AudioFilePathException extends Exception {
        private AudioFilePathException(String str) {
            super(str);
        }
    }
}
