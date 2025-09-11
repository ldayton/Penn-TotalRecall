package app.headless.adapters;

import core.services.FileSelectionService;
import java.io.File;
import java.util.Optional;

/**
 * Headless implementation of FileSelectionService that returns empty results. Used for testing and
 * headless environments where file selection is not available.
 */
public class HeadlessFileSelectionService implements FileSelectionService {

    @Override
    public Optional<File> selectFile(FileSelectionRequest request) {
        // In headless mode, file selection always returns empty
        return Optional.empty();
    }

    @Override
    public File[] selectMultipleFiles(FileSelectionRequest request) {
        // In headless mode, return empty array
        return new File[0];
    }
}
