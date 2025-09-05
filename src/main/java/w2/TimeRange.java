package w2;

/** Time range that the session knows about. */
public record TimeRange(double startSeconds, double endSeconds) {

    public TimeRange {
        if (startSeconds < 0 || endSeconds <= startSeconds) {
            throw new IllegalArgumentException("Invalid time range");
        }
    }

    public double durationSeconds() {
        return endSeconds - startSeconds;
    }

    public boolean contains(double timeSeconds) {
        return timeSeconds >= startSeconds && timeSeconds <= endSeconds;
    }
}
