package behaviors.singleact;

import java.awt.event.ActionEvent;
import util.GiveMessage;

/**
 * Displays a dialog containing information on available keybindings (and mouse actions) not listed
 * in {@link components.MyMenu};
 */
public class TipsMessageAction extends IdentifiedSingleAction {

    private String makeMessage() {
        StringBuffer out = new StringBuffer();

        out.append(
                "<html><b>NOTE: You can learn most keyboard shortcuts through File -> Edit Keyboard"
                        + " Shorcuts.</b>\n\n");

        out.append(
                "Space -- Another binding for play/pause, useful when the cursor is not the text"
                        + " field\n\n");

        out.append("Emacs keyboard shortcuts can be used in the wordpool text field.\n\n");

        out.append(
                "You can remove files from the file list by hitting delete or backspace while they"
                        + " are selected.\n");
        out.append(
                "You can switch to a file in the file list by selecting it and hitting enter.\n");
        out.append(
                "You can switch the waveform position to a previous annotation by selecting it and"
                        + " hitting enter.\n");

        out.append(
                "You can enter a wordpool word into the text field by selecting it and hitting"
                        + " enter.\n\n");

        out.append(
                "Double clicking with the mouse also works in place of selecting items and hitting"
                        + " enter.\n\n");

        out.append(
                "Right-click context menus are available for items in the audio file list and the"
                        + " annotation table.\n");
        out.append(
                "To re-open a file that has already been marked as complete, use the context"
                        + " menu.\n\n");

        out.append("You can drag and drop audio files/folders onto the application.\n");
        out.append("You can drag and drop wordpool documents onto the application.");

        return out.toString();
    }

    public void actionPerformed(ActionEvent e) {
        GiveMessage.infoMessage(makeMessage());
    }

    @Override
    public void update() {}
}
