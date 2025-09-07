package ui;

import app.swing.SwingApp;
import java.awt.Point;
import javax.swing.JFrame;

public class DialogCentering {

    /**
     * Does the dirty work of figuring out where a <code>JFrame</code> should appear on the screen.
     * Tries to find a location that would make the <code>JFrame</code> centered in <code>MainFrame
     * </code>, the app's main window.
     *
     * @return The recommended <code>Point</code> for the top left hand point of the <code>
     *     PreferencesFrame</code>
     */
    public static Point chooseLocation(JFrame frame) {
        MainWindowAccess windowService = SwingApp.getInjectedInstance(MainWindowAccess.class);
        if (windowService == null) {
            throw new IllegalStateException("MainWindowAccess not available via DI");
        }
        final Point p = windowService.getLocationOnScreen();
        final int mfX = (int) p.getX();
        final int mfY = (int) p.getY();
        final int mfWidth = windowService.getWidth();
        final int mfHeight = windowService.getHeight();
        final int prefWidth = (int) frame.getSize().getWidth();
        final int prefHeight = (int) frame.getSize().getHeight();

        int prefX = 0;
        int prefY = 0;

        if (mfWidth < prefWidth) {
            prefX = mfX;
        } else {
            prefX = mfX + (mfWidth - prefWidth) / 2;
        }
        if (mfHeight < prefHeight) {
            prefY = mfY;
        } else {
            prefY = mfY + (mfHeight - prefHeight) / 2;
        }
        return new Point(prefX, prefY);
    }
}
