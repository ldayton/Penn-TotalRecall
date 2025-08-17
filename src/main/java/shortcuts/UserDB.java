package shortcuts;

import env.PreferencesManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserDB {
    private static final Logger logger = LoggerFactory.getLogger(UserDB.class);

    private final List<XAction> defaultXActions;
    private final XActionListener listener;
    private final PreferencesManager preferencesManager;

    private static final String NO_SHORTCUT = "#";

    public UserDB(
            @NonNull PreferencesManager preferencesManager,
            @NonNull List<XAction> defaultXActions,
            @NonNull XActionListener listener) {
        this.preferencesManager = preferencesManager;
        this.defaultXActions = defaultXActions;
        this.listener = listener;
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
        preferencesManager.putString(key, value);
    }

    public Shortcut retrieve(String id) {
        String key = id;
        String storedStr = preferencesManager.getString(key, NO_SHORTCUT);

        if (NO_SHORTCUT.equals(storedStr)) {
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
