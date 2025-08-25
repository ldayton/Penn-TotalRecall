package util;

import components.MainFrame;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Rectangle;
import lombok.NonNull;

/**
 * Service for managing window operations in the application.
 *
 * <p>Centralizes window management for better dependency injection and testability.
 */
@Singleton
public class WindowService {
    private final MainFrame mainFrame;

    @Inject
    public WindowService(@NonNull MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    /** Requests focus for the main window. */
    public void requestFocus() {
        mainFrame.requestFocusInWindow();
    }

    /**
     * Sets the title of the main window.
     *
     * @param title The new title
     */
    public void setTitle(@NonNull String title) {
        mainFrame.setTitle(title);
    }

    /**
     * Gets the bounds of the main window.
     *
     * @return The window bounds
     */
    public Rectangle getBounds() {
        return mainFrame.getBounds();
    }

    /**
     * Gets the extended state of the main window.
     *
     * @return The extended state
     */
    public int getExtendedState() {
        return mainFrame.getExtendedState();
    }

    /**
     * Gets the location of the main window on screen.
     *
     * @return The window location
     */
    public java.awt.Point getLocationOnScreen() {
        return mainFrame.getLocationOnScreen();
    }

    /**
     * Gets the width of the main window.
     *
     * @return The window width
     */
    public int getWidth() {
        return mainFrame.getWidth();
    }

    /**
     * Gets the height of the main window.
     *
     * @return The window height
     */
    public int getHeight() {
        return mainFrame.getHeight();
    }

    /**
     * Gets the size of the main window.
     *
     * @return The window size
     */
    public java.awt.Dimension getSize() {
        return mainFrame.getSize();
    }
}
