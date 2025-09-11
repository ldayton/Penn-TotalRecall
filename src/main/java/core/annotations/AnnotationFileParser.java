package core.annotations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import core.env.ProgramVersion;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

// Annotation is in same package now

@Singleton
public class AnnotationFileParser {

    private final ObjectMapper mapper;
    private final ProgramVersion programVersion;

    @Inject
    public AnnotationFileParser(
            @NonNull ObjectMapper mapper, @NonNull ProgramVersion programVersion) {
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.programVersion = programVersion;
    }

    public AnnotationFileParser(@NonNull ProgramVersion programVersion) {
        this(new ObjectMapper(), programVersion);
    }

    public List<Annotation> parse(@NonNull File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("Annotation file does not exist: " + file.getAbsolutePath());
        }
        var annotationFile = mapper.readValue(file, AnnotationFile.class);
        return annotationFile.getAnnotations();
    }

    public boolean removeAnnotation(@NonNull Annotation annToDelete, @NonNull File file)
            throws IOException {
        if (!file.exists()) {
            throw new IOException("Annotation file does not exist: " + file.getAbsolutePath());
        }
        var annotationFile = mapper.readValue(file, AnnotationFile.class);
        var removed = annotationFile.getAnnotations().removeIf(ann -> ann.equals(annToDelete));

        if (removed) {
            writeAtomically(file.toPath(), annotationFile);
        }

        return removed;
    }

    public void appendAnnotation(@NonNull Annotation ann, @NonNull File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("Annotation file does not exist: " + file.getAbsolutePath());
        }
        var annotationFile = mapper.readValue(file, AnnotationFile.class);
        var annotations = annotationFile.getAnnotations();

        // Check if this exact annotation already exists
        if (annotations.contains(ann)) {
            return; // Already exists, do nothing
        }

        var insertIndex =
                Collections.binarySearch(
                        annotations, ann, Comparator.comparingDouble(Annotation::time));
        if (insertIndex < 0) {
            insertIndex = -insertIndex - 1;
        }

        annotations.add(insertIndex, ann);
        writeAtomically(file.toPath(), annotationFile);
    }

    public boolean headerExists(@NonNull File file) throws IOException {
        if (!file.exists() || file.length() == 0) {
            return false;
        }
        var annotationFile = mapper.readValue(file, AnnotationFile.class);
        return annotationFile.getMetadata().isPresent();
    }

    public void prependHeader(@NonNull File file, @NonNull String annotatorName)
            throws IOException {

        if (!file.exists()) {
            throw new IOException("Annotation file does not exist: " + file.getAbsolutePath());
        }

        var annotationFile =
                file.length() > 0
                        ? mapper.readValue(file, AnnotationFile.class)
                        : new AnnotationFile();

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
                        this.programVersion.toString(),
                        systemInfo);

        annotationFile.setMetadata(metadata);
        writeAtomically(file.toPath(), annotationFile);
    }

    public void addField(@NonNull File file, @NonNull String fieldName, @NonNull String value)
            throws IOException {
        if (!file.exists()) {
            throw new IOException("Annotation file does not exist: " + file.getAbsolutePath());
        }
        var annotationFile = mapper.readValue(file, AnnotationFile.class);

        var metadata =
                annotationFile
                        .getMetadata()
                        .orElseGet(
                                () -> {
                                    var newMetadata = new AnnotationFile.Metadata();
                                    annotationFile.setMetadata(newMetadata);
                                    return newMetadata;
                                });

        var system =
                Objects.requireNonNullElseGet(
                        metadata.getSystem(), () -> new LinkedHashMap<String, String>());
        metadata.setSystem(system);

        system.put(fieldName, value);
        writeAtomically(file.toPath(), annotationFile);
    }

    private void writeAtomically(@NonNull Path target, @NonNull AnnotationFile content)
            throws IOException {
        var tempFile = Files.createTempFile("ann", ".ann.json");
        try {
            mapper.writeValue(tempFile.toFile(), content);
            Files.move(
                    tempFile,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
