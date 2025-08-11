package components;

import control.XActionManager;
import edu.upenn.psych.memory.shortcutmanager.ShortcutManager;
import java.net.URL;

public class ShortcutFrame extends ShortcutManager {

    private ShortcutFrame(URL url, String namespace) {
        super(url, namespace, XActionManager.listener);
    }

    public static ShortcutFrame instance =
            new ShortcutFrame(
                    ShortcutFrame.class.getResource("/actions.xml"),
                    "/edu/upenn/psych/memory/penntotalrecall");
}
