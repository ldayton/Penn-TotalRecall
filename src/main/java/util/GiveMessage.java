package util;

import behaviors.singleact.AboutAction;
import components.MyFrame;
import info.Constants;
import info.GUIConstants;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

/** Utility class for launching dialogs with consistent appearance. */
public class GiveMessage {

    /**
     * Launches an error dialog with the provided message.
     *
     * @param message The error message to display
     */
    public static void errorMessage(String message) {
        JOptionPane.showMessageDialog(
                MyFrame.getInstance(),
                message,
                GUIConstants.errorDialogTitle,
                JOptionPane.ERROR_MESSAGE,
                new ImageIcon(AboutAction.class.getResource("/images/headphones48.png")));
    }

    /**
     * Launches an info dialog with the provided message.
     *
     * @param message The info message to display
     */
    public static void infoMessage(String message) {
        JOptionPane.showMessageDialog(
                MyFrame.getInstance(),
                message,
                Constants.programName,
                JOptionPane.OK_OPTION,
                new ImageIcon(AboutAction.class.getResource("/images/headphones48.png")));
    }

    public static String inputMessage(String message) {
        Object input =
                JOptionPane.showInputDialog(
                        MyFrame.getInstance(),
                        message,
                        Constants.programName,
                        JOptionPane.OK_CANCEL_OPTION,
                        new ImageIcon(AboutAction.class.getResource("/images/headphones48.png")),
                        null,
                        "");
        if (input instanceof String) {
            return (String) input;
        } else {
            return null;
        }
    }
}
