package components;

import control.XActionManager;
import env.Environment;
import java.net.URL;
import shortcuts.ShortcutManager;

public class ShortcutFrame extends ShortcutManager {

    public ShortcutFrame(URL url, String namespace, Environment environment) {
        super(url, namespace, XActionManager.listener, environment);
    }

    public static ShortcutFrame createDefault(Environment environment) {
        return new ShortcutFrame(
                ShortcutFrame.class.getResource("/actions.xml"),
                "/edu/upenn/psych/memory/penntotalrecall",
                environment);
    }

    public static void showShortcutEditor() {
        var frame = createDefault(new Environment());
        frame.setLocation(util.GUIUtils.chooseLocation(frame));
        frame.setVisible(true);
    }
}
