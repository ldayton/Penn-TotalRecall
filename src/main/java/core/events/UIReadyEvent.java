package core.events;

/**
 * Event published when the UI is fully ready for interaction. This occurs after the main window is
 * visible, components are sized, and the initial paint cycle has completed.
 *
 * <p>Used to coordinate actions that require the UI to be fully initialized, such as auto-loading
 * files in development mode.
 */
public class UIReadyEvent {
    // No additional data needed - this is a signal event
}
