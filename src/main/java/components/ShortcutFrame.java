package components;

import control.XActionManager;
import java.net.URL;
import shortcuts.ShortcutManager;

public class ShortcutFrame extends ShortcutManager {

    private ShortcutFrame(URL url, String namespace) {
        super(url, namespace, XActionManager.listener);
    }

    public static final ShortcutFrame instance =
            new ShortcutFrame(
                    ShortcutFrame.class.getResource("/actions.xml"),
                    "/edu/upenn/psych/memory/penntotalrecall");
}
