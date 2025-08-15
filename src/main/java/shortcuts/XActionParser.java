package shortcuts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import env.Environment;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XActionParser {
    private final URL url;
    private final Environment environment;

    public XActionParser(URL url, Environment environment) {
        this.url = url;
        this.environment = environment;
    }

    public List<XAction> getXactions() {
        try {
            List<XAction> acts = parseXActions();

            // Check for duplicates
            List<Shortcut> shortcuts = new ArrayList<>();
            List<String> ids = new ArrayList<>();

            for (XAction act : acts) {
                if (act.shortcut() != null) {
                    shortcuts.add(act.shortcut());
                }
                ids.add(act.getId());
            }

            assertNoDups(ids, "ID");
            assertNoDups(shortcuts, "shortcut");

            return acts;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse XActions", e);
        }
    }

    private <T> void assertNoDups(List<T> list, String type) {
        Set<T> seen = new HashSet<>();
        for (T item : list) {
            if (!seen.add(item)) {
                throw new ShortcutFileFormatException(
                        "shortcuts file contains duplicate " + type + ": " + item);
            }
        }
    }

    private List<XAction> parseXActions() throws Exception {
        XmlMapper mapper = new XmlMapper();
        ActionsRoot root = mapper.readValue(url.openStream(), ActionsRoot.class);

        List<XAction> xactions = new ArrayList<>();
        for (ActionElement actionEl : root.actions) {
            XAction action = parseAction(actionEl);
            if (action != null) {
                xactions.add(action);
            }
        }

        return xactions;
    }

    private XAction parseAction(ActionElement actionEl) {
        String name = actionEl.name;
        String clazz = actionEl.className;
        String tooltip = actionEl.tooltip;
        String argValue = actionEl.enumValue;
        String osValue = actionEl.os;

        // Check OS compatibility
        if (osValue != null) {
            var goodOSes = List.of(osValue.split(","));
            String currentOS = System.getProperty("os.name");
            if (!goodOSes.contains(currentOS)) {
                return null;
            }
        }

        XAction baseXAction = new XAction(clazz, argValue, name, tooltip, null);

        if (actionEl.shortcut == null) {
            return baseXAction;
        } else {
            Shortcut shortcut = parseShortcut(actionEl.shortcut);
            if (shortcut != null) {
                return baseXAction.withShortcut(shortcut);
            } else {
                return null;
            }
        }
    }

    private Shortcut parseShortcut(ShortcutElement shortcutEl) {
        List<String> maskKeyNames = new ArrayList<>();
        List<String> nonMaskKeyNames = new ArrayList<>();

        if (shortcutEl.masks != null) {
            for (KeyElement mask : shortcutEl.masks) {
                maskKeyNames.add(mask.keyname);
            }
        }

        if (shortcutEl.keys != null) {
            for (KeyElement key : shortcutEl.keys) {
                nonMaskKeyNames.add(key.keyname);
            }
        }

        return Shortcut.fromXmlForm(maskKeyNames, nonMaskKeyNames, environment);
    }

    @JacksonXmlRootElement(localName = "actions")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ActionsRoot {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JsonProperty("action")
        public final List<ActionElement> actions = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ActionElement {
        @JacksonXmlProperty(isAttribute = true, localName = "class")
        public String className;

        @JacksonXmlProperty(isAttribute = true)
        public String name;

        @JacksonXmlProperty(isAttribute = true)
        public String tooltip;

        @JacksonXmlProperty(isAttribute = true, localName = "enum")
        public String enumValue;

        @JacksonXmlProperty(isAttribute = true)
        public String os;

        @JsonProperty("shortcut")
        public ShortcutElement shortcut;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ShortcutElement {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JsonProperty("mask")
        public final List<KeyElement> masks = new ArrayList<>();

        @JacksonXmlElementWrapper(useWrapping = false)
        @JsonProperty("key")
        public final List<KeyElement> keys = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class KeyElement {
        @JacksonXmlProperty(isAttribute = true)
        public String keyname;
    }

    public static class ShortcutFileFormatException extends RuntimeException {
        public ShortcutFileFormatException(String msg) {
            super(msg);
        }
    }
}
