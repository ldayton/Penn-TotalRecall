package control;

import behaviors.singleact.CheckUpdatesAction;
import components.MyFocusTraversalPolicy;
import components.MyFrame;
import components.MyMenu;
import components.MySplitPane;
import components.ShortcutFrame;
import info.Constants;
import info.SysInfo;
import info.UserPrefs;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Entry point class of the entire program. */
public class Start {
    private static boolean DEV_MODE;

    public static final boolean DEBUG_FOCUS = false;

    /**
     * True entry point of program after Swing thread is created.
     *
     * <p>First makes look and feel customizations for Mac OSX and other platforms, and then
     * launches main program window. Creates and runs thread to check for updates.
     */
    private Start() {
        System.out.println(
                Constants.programName + " current directory: " + new File(".").getAbsolutePath());
        System.out.println(
                Constants.programName
                        + " detected architecture: "
                        + System.getProperty("sun.arch.data.model"));
        // Temporarily disabled macOS customization due to deprecated Apple EAWT APIs
        // if(SysInfo.sys.isMacOSX) {
        // 	MacOSXCustomizer.customizeForMacOSX();
        // }

        try {
            if (SysInfo.sys.useMetalLAF) {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } else {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        var unused = ShortcutFrame.instance.toString(); // initialize
        MyFrame.getInstance(); // creates all the components, so after this line everything is
        // made, just not visible
        MyFrame.getInstance().setFocusTraversalPolicy(new MyFocusTraversalPolicy());
        MyMenu.updateActions(); // set up all action states before frame becomes visible but after
        // all components tied to the actions are made
        restoreFramePositionAndLayout();
        MyFrame.getInstance().setVisible(true);
        if (DEV_MODE == false) {
            new CheckUpdatesAction(false)
                    .actionPerformed(
                            new ActionEvent(
                                    MyFrame.getInstance(), ActionEvent.ACTION_PERFORMED, null));
        }
    }

    /**
     * Attempts to restore program window's frame size, position, and internal split pane divider
     * location using saved values.
     *
     * <p>If saved values are not available, defaults are used.
     */
    private void restoreFramePositionAndLayout() {
        MyFrame frame = MyFrame.getInstance();

        if (UserPrefs.prefs.getBoolean(
                UserPrefs.windowMaximized, UserPrefs.defaultWindowMaximized)) {
            frame.setLocation(0, 0);
            frame.setBounds(0, 0, UserPrefs.defaultWindowWidth, UserPrefs.defaultWindowHeight);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            int lastX = 0;
            int lastY = 0;
            int lastWidth = 0;
            int lastHeight = 0;
            try {
                lastX = UserPrefs.prefs.getInt(UserPrefs.windowXLocation, 0);
                lastY = UserPrefs.prefs.getInt(UserPrefs.windowYLocation, 0);
                lastWidth =
                        UserPrefs.prefs.getInt(UserPrefs.windowWidth, UserPrefs.defaultWindowWidth);
                lastHeight =
                        UserPrefs.prefs.getInt(
                                UserPrefs.windowHeight, UserPrefs.defaultWindowHeight);
            } catch (NumberFormatException e) {
                lastX = 0;
                lastY = 0;
                lastWidth = 1000;
                lastHeight = 500;
            }
            frame.setLocation(lastX, lastY);
            frame.setBounds(new Rectangle(lastX, lastY, lastWidth, lastHeight));
        }

        int dividerLocation = 0;
        int halfway =
                UserPrefs.prefs.getInt(UserPrefs.windowHeight, UserPrefs.defaultWindowHeight) / 2;
        dividerLocation = UserPrefs.prefs.getInt(UserPrefs.dividerLocation, halfway);
        MySplitPane.getInstance().setDividerLocation(dividerLocation);
    }

    public static boolean developerMode() {
        return DEV_MODE;
    }

    /**
     * Program entry point. Only used to create an object of this class running on the event
     * dispatch thread, as per Java Swing policy.
     *
     * @param args Ignored
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            if (args[0].equals("-developer")) {
                DEV_MODE = true;
                System.out.println("Running " + Constants.programName + " in developer mode.");
            } else {
                DEV_MODE = false;
            }
        }
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run() {
                        new Start();
                    }
                });
    }
}
