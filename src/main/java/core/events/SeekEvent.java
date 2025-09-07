package core.events;

/** Request to seek playback to an absolute frame. */
public record SeekEvent(long frame) {}
