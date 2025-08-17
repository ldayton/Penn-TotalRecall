package components;

import control.XActionManager;
import java.net.URL;
import shortcuts.ShortcutManager;

public class ShortcutFrame extends ShortcutManager {

    public ShortcutFrame(URL url) {
        super(url, XActionManager.listener);
    }

    public static ShortcutFrame createDefault() {
        return new ShortcutFrame(ShortcutFrame.class.getResource("/actions.xml"));
    }

    public static void showShortcutEditor() {
        var frame = createDefault();
        frame.setLocation(util.GUIUtils.chooseLocation(frame));
        frame.setVisible(true);
    }
}
