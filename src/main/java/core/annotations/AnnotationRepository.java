package core.annotations;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Repository interface for persisting and loading annotations. */
public interface AnnotationRepository {

    /**
     * Loads annotations from the specified file.
     *
     * @param file the file to load from
     * @return list of loaded annotation entries
     * @throws IOException if loading fails
     */
    List<AnnotationEntry> load(Path file) throws IOException;

    /**
     * Saves annotations to the specified file.
     *
     * @param annotations the annotations to save
     * @param file the file to save to
     * @throws IOException if saving fails
     */
    void save(List<AnnotationEntry> annotations, Path file) throws IOException;

    /**
     * Checks if a file has a valid annotation header.
     *
     * @param file the file to check
     * @return true if the file has a valid header
     * @throws IOException if reading fails
     */
    boolean hasValidHeader(Path file) throws IOException;

    /**
     * Creates a new annotation file with header.
     *
     * @param file the file to create
     * @param annotatorName the name of the annotator
     * @throws IOException if creation fails
     */
    void createFile(Path file, String annotatorName) throws IOException;
}
