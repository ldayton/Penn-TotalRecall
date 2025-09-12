package core.events;

/** Event published when zoom is requested for the waveform display. */
public record ZoomEvent(Direction direction) {
    public enum Direction {
        IN,
        OUT
    }
}
