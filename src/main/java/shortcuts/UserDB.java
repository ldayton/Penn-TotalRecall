package shortcuts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserDB {
    private static final Logger logger = LoggerFactory.getLogger(UserDB.class);

    @SuppressWarnings("UnusedVariable") // Used in constructor for prefs.node()
    private final String namespace;

    private final List<XAction> defaultXActions;
    private final XActionListener listener;
    private final Preferences prefs;

    private static final String NO_SHORTCUT = "#";

    public UserDB(String namespace, List<XAction> defaultXActions, XActionListener listener) {
        if (!namespace.startsWith("/")) {
            throw new IllegalArgumentException("namespace " + namespace + " is not absolute");
        }

        this.namespace = namespace;
        this.defaultXActions = defaultXActions;
        this.listener = listener;
        this.prefs = Preferences.userRoot().node(namespace);
    }

    public void store(XAction xaction) {
        String key = xaction.getId();
        Shortcut oldShortcut = retrieveAll().get(key);

        String value;
        if (xaction.shortcut() != null) {
            value = xaction.shortcut().getInternalForm();
        } else {
            value = NO_SHORTCUT;
        }

        listener.xActionUpdated(xaction, oldShortcut);
        prefs.put(key, value);
    }

    public Shortcut retrieve(String id) {
        String key = id;
        String storedStr = prefs.get(key, null);

        if (storedStr == null || NO_SHORTCUT.equals(storedStr)) {
            return null;
        } else {
            Shortcut shortcut = Shortcut.fromInternalForm(storedStr);
            if (shortcut == null) {
                logger.warn(getClass().getName() + " won't retrieve() unparseable: " + storedStr);
                return null;
            }
            return shortcut;
        }
    }

    public void persistDefaults(boolean overwrite) {
        for (XAction xact : defaultXActions) {
            if (overwrite || retrieve(xact.getId()) == null) {
                store(xact);
            }
        }
    }

    public Map<String, Shortcut> retrieveAll() {
        Map<String, Shortcut> result = new HashMap<>();
        for (XAction xAction : defaultXActions) {
            String id = xAction.getId();
            result.put(id, retrieve(id));
        }
        return result;
    }
}
