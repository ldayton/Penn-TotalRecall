package core.events;

import java.util.Optional;
import java.util.Set;

/**
 * Represents a request for file selection from the user. This abstraction allows core actions to
 * request file selection without depending on specific UI implementations.
 */
public record FileSelectionRequest(
        String title, String initialPath, SelectionMode mode, Optional<FileFilter> filter) {

    public enum SelectionMode {
        FILES_ONLY,
        DIRECTORIES_ONLY,
        FILES_AND_DIRECTORIES
    }

    /** File filter abstraction that doesn't depend on Swing. */
    public record FileFilter(
            String description, Set<String> extensions, boolean acceptDirectories) {

        /** Tests whether the given filename should be accepted. */
        public boolean accept(String filename, boolean isDirectory) {
            if (isDirectory) {
                return acceptDirectories;
            }
            if (extensions.isEmpty()) {
                return true; // Accept all files if no extensions specified
            }
            String lowerName = filename.toLowerCase();
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
            return new FileSelectionRequest(title, initialPath, mode, Optional.ofNullable(filter));
        }
    }
}
