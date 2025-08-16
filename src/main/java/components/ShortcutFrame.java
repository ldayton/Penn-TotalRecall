package components;

import control.XActionManager;
import java.net.URL;
import shortcuts.ShortcutManager;

public class ShortcutFrame extends ShortcutManager {

    public ShortcutFrame(URL url, String namespace) {
        super(url, namespace, XActionManager.listener);
    }

    public static ShortcutFrame createDefault() {
        return new ShortcutFrame(
                ShortcutFrame.class.getResource("/actions.xml"),
                "/edu/upenn/psych/memory/penntotalrecall");
    }

    public static void showShortcutEditor() {
        var frame = createDefault();
        frame.setLocation(util.GUIUtils.chooseLocation(frame));
        frame.setVisible(true);
    }
}
