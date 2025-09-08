package core.services;

import java.io.File;
import java.util.Optional;
import java.util.Set;

/**
 * Service interface for file selection operations. This abstraction allows core actions to request
 * file selection without depending on specific UI implementations (Swing/AWT).
 *
 * <p>The UI layer provides an implementation that uses platform-specific dialogs.
 */
public interface FileSelectionService {

    /**
     * Shows a file selection dialog to the user.
     *
     * @param request the file selection request parameters
     * @return the selected file, or empty if cancelled
     */
    Optional<File> selectFile(FileSelectionRequest request);

    /**
     * Shows a file selection dialog for selecting multiple files.
     *
     * @param request the file selection request parameters
     * @return array of selected files, or empty array if cancelled
     */
    File[] selectMultipleFiles(FileSelectionRequest request);

    /** Represents a request for file selection from the user. */
    record FileSelectionRequest(
            String title, String initialPath, SelectionMode mode, Optional<FileFilter> filter) {

        public enum SelectionMode {
            FILES_ONLY,
            DIRECTORIES_ONLY,
            FILES_AND_DIRECTORIES
        }

        /** File filter abstraction that doesn't depend on Swing. */
        public record FileFilter(
                String description, Set<String> extensions, boolean acceptDirectories) {

            /** Tests whether the given file should be accepted. */
            public boolean accept(File file) {
                if (file.isDirectory()) {
                    return acceptDirectories;
                }
                if (extensions.isEmpty()) {
                    return true; // Accept all files if no extensions specified
                }
                String lowerName = file.getName().toLowerCase();
                return extensions.stream().anyMatch(lowerName::endsWith);
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String title = "Select File";
            private String initialPath = System.getProperty("user.home");
            private SelectionMode mode = SelectionMode.FILES_ONLY;
            private FileFilter filter = null;

            public Builder title(String title) {
                this.title = title;
                return this;
            }

            public Builder initialPath(String path) {
                this.initialPath = path;
                return this;
            }

            public Builder mode(SelectionMode mode) {
                this.mode = mode;
                return this;
            }

            public Builder filter(FileFilter filter) {
                this.filter = filter;
                return this;
            }

            public Builder filter(String description, Set<String> extensions) {
                this.filter = new FileFilter(description, extensions, true);
                return this;
            }

            public FileSelectionRequest build() {
                return new FileSelectionRequest(
                        title, initialPath, mode, Optional.ofNullable(filter));
            }
        }
    }
}
