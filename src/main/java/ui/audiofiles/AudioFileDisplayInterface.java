package ui.audiofiles;

import java.io.File;
import lombok.NonNull;

/** Interface for AudioFileDisplay to break circular dependency with AudioFileList. */
public interface AudioFileDisplayInterface {
    /**
     * Adds files to the AudioFileList if they are supported audio files.
     *
     * @param files Candidate files to be added
     * @return true if any of the files were ultimately added
     */
    boolean addFilesIfSupported(@NonNull File[] files);

    /**
     * Switches to the provided AudioFile after asking for user confirmation if needed.
     *
     * @param file The file that may be switched to
     * @return true iff the file switch took place
     */
    boolean askToSwitchFile(@NonNull AudioFile file);
}
