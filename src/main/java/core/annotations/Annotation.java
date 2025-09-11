package core.annotations;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Note: this class has a natural ordering that is inconsistent with equals. */
public record Annotation(
        @JsonProperty("time") double time,
        @JsonProperty("wordNum") int wordNum,
        @JsonProperty("text") String text)
        implements Comparable<Annotation> {

    public Annotation {
        text = text != null ? text : "";
    }

    @Override
    public int compareTo(Annotation o) {
        return Double.compare(time, o.time);
    }

    @Override
    public String toString() {
        return "Annotation: " + text + " " + time + " ms " + " #" + wordNum;
    }
}
