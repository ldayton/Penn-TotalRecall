package ui;

import core.services.FileSelectionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.util.Optional;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

/**
 * Swing implementation of FileSelectionService. Uses JFileChooser to show file selection dialogs.
 */
@Singleton
public class SwingFileSelectionService implements FileSelectionService {

    private final DialogService dialogService;

    @Inject
    public SwingFileSelectionService(DialogService dialogService) {
        this.dialogService = dialogService;
    }

    @Override
    public Optional<File> selectFile(FileSelectionRequest request) {
        int mode =
                switch (request.mode()) {
                    case FILES_ONLY -> JFileChooser.FILES_ONLY;
                    case DIRECTORIES_ONLY -> JFileChooser.DIRECTORIES_ONLY;
                    case FILES_AND_DIRECTORIES -> JFileChooser.FILES_AND_DIRECTORIES;
                };

        FileFilter swingFilter = request.filter().map(this::toSwingFilter).orElse(null);

        File result =
                dialogService.showFileChooser(
                        request.title(), request.initialPath(), mode, swingFilter);

        return Optional.ofNullable(result);
    }

    @Override
    public File[] selectMultipleFiles(FileSelectionRequest request) {
        // For now, just use single selection
        // Could be enhanced to support multi-selection in JFileChooser
        return selectFile(request).map(file -> new File[] {file}).orElse(new File[0]);
    }

    private FileFilter toSwingFilter(FileSelectionRequest.FileFilter filter) {
        return new FileFilter() {
            @Override
            public boolean accept(File f) {
                return filter.accept(f);
            }

            @Override
            public String getDescription() {
                return filter.description();
            }
        };
    }
}
