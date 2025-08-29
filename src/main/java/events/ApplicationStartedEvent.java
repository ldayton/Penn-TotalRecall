package events;

/**
 * Event fired when the application has fully started and the UI is visible.
 *
 * <p>This event is published to EventDispatchBus after all components are initialized and the main
 * window is displayed.
 */
public record ApplicationStartedEvent() {}
