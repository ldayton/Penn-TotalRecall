package core.annotations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import core.env.Constants;
import core.env.ProgramVersion;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File-based implementation of AnnotationRepository. Handles reading and writing annotation files
 * in JSON format.
 */
@Singleton
public class FileAnnotationRepository implements AnnotationRepository {
    private static final Logger logger = LoggerFactory.getLogger(FileAnnotationRepository.class);

    private final ObjectMapper mapper;
    private final ProgramVersion programVersion;

    @Inject
    public FileAnnotationRepository(ObjectMapper mapper, ProgramVersion programVersion) {
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.programVersion = programVersion;
    }

    @Override
    public List<AnnotationEntry> load(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("Annotation file does not exist: " + file);
        }

        var annotationFile = mapper.readValue(file.toFile(), AnnotationFile.class);
        var metadata = annotationFile.getMetadata().orElse(null);

        // Get annotator name from metadata, or use default
        String annotatorName = metadata != null ? metadata.getAnnotator() : "Unknown";

        // Convert Annotation objects to AnnotationEntry objects
        return annotationFile.getAnnotations().stream()
                .map(
                        ann -> {
                            // Determine type based on text content
                            AnnotationType type = determineAnnotationType(ann.text());

                            // Use metadata creation time if available, otherwise use current time
                            Instant createdAt =
                                    metadata != null && metadata.getUnixTimestamp() > 0
                                            ? Instant.ofEpochSecond(metadata.getUnixTimestamp())
                                            : Instant.now();

                            return AnnotationEntry.createWithTimestamp(
                                    ann, type, annotatorName, createdAt);
                        })
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public void save(List<AnnotationEntry> annotations, Path file) throws IOException {
        Objects.requireNonNull(annotations, "annotations cannot be null");
        Objects.requireNonNull(file, "file cannot be null");

        // Get annotator name from first annotation or use default
        String annotatorName =
                annotations.isEmpty() ? "Unknown" : annotations.get(0).annotatorName();

        // Create metadata
        var systemInfo = new LinkedHashMap<String, String>();
        systemInfo.put("os", System.getProperty("os.name"));
        systemInfo.put("arch", System.getProperty("os.arch"));
        systemInfo.put("user", System.getProperty("user.name"));
        systemInfo.put("java_version", System.getProperty("java.version"));

        var now = Instant.now();
        var metadata =
                new AnnotationFile.Metadata(
                        annotatorName,
                        DateTimeFormatter.ISO_INSTANT.format(now),
                        now.getEpochSecond(),
                        programVersion.toString(),
                        systemInfo);

        // Convert AnnotationEntry objects to Annotation objects
        var annotationList =
                annotations.stream()
                        .map(AnnotationEntry::annotation)
                        .sorted()
                        .collect(Collectors.toList());

        var annotationFile = new AnnotationFile(metadata, annotationList);

        // Write atomically to avoid corruption
        writeAtomically(file, annotationFile);

        logger.debug("Saved {} annotations to {}", annotations.size(), file);
    }

    @Override
    public boolean hasValidHeader(Path file) throws IOException {
        if (!Files.exists(file) || Files.size(file) == 0) {
            return false;
        }

        try {
            var annotationFile = mapper.readValue(file.toFile(), AnnotationFile.class);
            return annotationFile.getMetadata().isPresent();
        } catch (Exception e) {
            logger.warn("Failed to check header for file: {}", file, e);
            return false;
        }
    }

    @Override
    public void createFile(Path file, String annotatorName) throws IOException {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(annotatorName, "annotatorName cannot be null");

        if (Files.exists(file)) {
            throw new IOException("File already exists: " + file);
        }

        // Create metadata
        var systemInfo = new LinkedHashMap<String, String>();
        systemInfo.put("os", System.getProperty("os.name"));
        systemInfo.put("arch", System.getProperty("os.arch"));
        systemInfo.put("user", System.getProperty("user.name"));
        systemInfo.put("java_version", System.getProperty("java.version"));

        var now = Instant.now();
        var metadata =
                new AnnotationFile.Metadata(
                        annotatorName,
                        DateTimeFormatter.ISO_INSTANT.format(now),
                        now.getEpochSecond(),
                        programVersion.toString(),
                        systemInfo);

        var annotationFile = new AnnotationFile(metadata, new ArrayList<>());

        // Create parent directories if needed
        Files.createDirectories(file.getParent());

        // Write the file
        mapper.writeValue(file.toFile(), annotationFile);

        logger.debug("Created new annotation file: {}", file);
    }

    /** Writes the annotation file atomically to prevent corruption. */
    private void writeAtomically(Path file, AnnotationFile annotationFile) throws IOException {
        // Write to temp file first
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");

        try {
            mapper.writeValue(tempFile.toFile(), annotationFile);

            // Atomically move temp file to target
            Files.move(
                    tempFile,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Clean up temp file on failure
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    /** Determines the annotation type based on the text content. */
    private AnnotationType determineAnnotationType(String text) {
        if (text == null || text.isEmpty()) {
            return AnnotationType.CUSTOM;
        }

        // Check for intrusion marker
        if (text.equals(Constants.intrusionSoundString)
                || text.toLowerCase().contains("intrusion")) {
            return AnnotationType.INTRUSION;
        }

        // TODO: Check against wordpool to determine if REGULAR or CUSTOM
        // For now, default to REGULAR for non-empty text
        return AnnotationType.REGULAR;
    }
}
