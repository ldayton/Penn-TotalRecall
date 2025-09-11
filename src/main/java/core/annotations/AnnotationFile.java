package core.annotations;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Annotation is in same package now

/** Container for annotation file data including metadata and annotations. */
public class AnnotationFile {

    @JsonProperty private Metadata metadata;
    @JsonProperty private List<Annotation> annotations;

    public AnnotationFile() {
        this.metadata = null;
        this.annotations = new ArrayList<>();
    }

    public AnnotationFile(Metadata metadata, List<Annotation> annotations) {
        this.metadata = metadata;
        this.annotations = annotations != null ? annotations : new ArrayList<>();
    }

    public Optional<Metadata> getMetadata() {
        return Optional.ofNullable(metadata);
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public List<Annotation> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }

    public static class Metadata {
        @JsonProperty private String annotator;
        @JsonProperty private String created;

        @JsonProperty("unix_timestamp")
        private long unixTimestamp;

        @JsonProperty("program_version")
        private String programVersion;

        @JsonProperty private Map<String, String> system;

        public Metadata() {}

        public Metadata(
                String annotator,
                String created,
                long unixTimestamp,
                String programVersion,
                Map<String, String> system) {
            this.annotator = annotator;
            this.created = created;
            this.unixTimestamp = unixTimestamp;
            this.programVersion = programVersion;
            this.system = system;
        }

        public String getAnnotator() {
            return annotator;
        }

        public void setAnnotator(String annotator) {
            this.annotator = annotator;
        }

        public String getCreated() {
            return created;
        }

        public void setCreated(String created) {
            this.created = created;
        }

        public long getUnixTimestamp() {
            return unixTimestamp;
        }

        public void setUnixTimestamp(long unixTimestamp) {
            this.unixTimestamp = unixTimestamp;
        }

        public String getProgramVersion() {
            return programVersion;
        }

        public void setProgramVersion(String programVersion) {
            this.programVersion = programVersion;
        }

        public Map<String, String> getSystem() {
            return system;
        }

        public void setSystem(Map<String, String> system) {
            this.system = system;
        }
    }
}
