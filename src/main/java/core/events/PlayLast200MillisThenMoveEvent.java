package core.events;

/** Event published when a move plus replay last 200ms is requested. */
public record PlayLast200MillisThenMoveEvent(boolean forward) {}
