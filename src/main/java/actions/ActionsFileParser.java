package actions;

import static java.util.stream.Collectors.joining;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import env.KeyboardManager;
import env.Platform;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.swing.KeyStroke;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shortcuts.Shortcut;

/**
 * Jackson-based parser for actions.xml configuration files.
 *
 * <p>Note: This parser uses Jackson's XML module which internally uses the JSON processing engine.
 * The @JsonProperty annotations are required alongside @JacksonXmlProperty annotations to properly
 * map XML elements and attributes to Java fields.
 */
public class ActionsFileParser {
    private static final Logger logger = LoggerFactory.getLogger(ActionsFileParser.class);

    private final XmlMapper xmlMapper;
    private final Platform platform;
    private final KeyboardManager keyboardManager;

    @Inject
    public ActionsFileParser(@NonNull Platform platform, @NonNull KeyboardManager keyboardManager) {
        this.xmlMapper = createConfiguredMapper();
        this.platform = platform;
        this.keyboardManager = keyboardManager;
    }

    /** Parses actions.xml from the given URL. */
    public List<ActionConfig> parseActions(@NonNull URL url) throws ActionParseException {
        logger.debug("Parsing actions from: {}", url);

        try (InputStream inputStream = url.openStream()) {
            ActionsDocument document = xmlMapper.readValue(inputStream, ActionsDocument.class);
            return document.toActionConfigs(platform, keyboardManager);
        } catch (IOException e) {
            throw new ActionParseException("Failed to parse actions.xml from " + url, e);
        }
    }

    /** Parses actions.xml from the classpath. */
    public List<ActionConfig> parseActionsFromClasspath(@NonNull String resourcePath)
            throws ActionParseException {
        URL resource = getClass().getResource(resourcePath);
        if (resource == null) {
            throw new ActionParseException("Resource not found: " + resourcePath);
        }
        return parseActions(resource);
    }

    /** Creates a properly configured XmlMapper following Jackson best practices. */
    private XmlMapper createConfiguredMapper() {
        XmlMapper mapper = new XmlMapper();

        // Configure for strict parsing - fail fast on any issues
        mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                true);
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                true);

        return mapper;
    }

    /** Root document structure for actions.xml. */
    @JacksonXmlRootElement(localName = "actions")
    public static final class ActionsDocument {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JsonProperty(
                "action") // Required for Jackson XML module to map XML elements to Java fields
        private final List<ActionElement> actions;

        // Jackson requires default constructor
        private ActionsDocument() {
            this.actions = new ArrayList<>();
        }

        public ActionsDocument(List<ActionElement> actions) {
            this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
        }

        public List<ActionConfig> toActionConfigs(
                Platform platform, KeyboardManager keyboardManager) throws ActionParseException {
            List<ActionConfig> result = new ArrayList<>();
            for (ActionElement element : actions) {
                Optional<ActionConfig> config = element.toActionConfig(platform, keyboardManager);
                if (config.isPresent()) {
                    result.add(config.get());
                }
            }
            return result;
        }
    }

    /** Individual action element from XML. */
    public static final class ActionElement {
        @JacksonXmlProperty(isAttribute = true, localName = "class")
        private final String className;

        @JacksonXmlProperty(isAttribute = true)
        private final String name;

        @JacksonXmlProperty(isAttribute = true)
        private final String tooltip;

        @JacksonXmlProperty(isAttribute = true, localName = "enum")
        private final String enumValue;

        @JacksonXmlProperty(isAttribute = true)
        private final String os;

        @JsonProperty("shortcut")
        private final ShortcutElement shortcut;

        // Jackson requires default constructor
        private ActionElement() {
            this.className = null;
            this.name = null;
            this.tooltip = null;
            this.enumValue = null;
            this.os = null;
            this.shortcut = null;
        }

        public ActionElement(
                String className,
                String name,
                String tooltip,
                String enumValue,
                String os,
                ShortcutElement shortcut) {
            this.className = className;
            this.name = name;
            this.tooltip = tooltip;
            this.enumValue = enumValue;
            this.os = os;
            this.shortcut = shortcut;
        }

        /** Converts this element to an ActionConfig, applying OS filtering. */
        public Optional<ActionConfig> toActionConfig(
                Platform platform, KeyboardManager keyboardManager) throws ActionParseException {
            try {
                // Check OS compatibility first
                Set<Platform.PlatformType> compatiblePlatforms = parseCompatiblePlatforms(os);
                if (!compatiblePlatforms.isEmpty()
                        && !compatiblePlatforms.contains(platform.detect())) {
                    logger.debug("Skipping action {} - not compatible with current OS", className);
                    return Optional.empty();
                }

                // Parse shortcut if present
                Optional<Shortcut> parsedShortcut = parseShortcut(keyboardManager);

                // ActionConfig constructor will validate required fields - let exceptions bubble up
                return Optional.of(
                        new ActionConfig(className, name, tooltip, enumValue, parsedShortcut));
            } catch (IllegalArgumentException e) {
                // Fail fast - don't skip invalid actions
                throw new ActionParseException(
                        "Invalid action configuration: " + e.getMessage(), e);
            }
        }

        private Optional<Shortcut> parseShortcut(KeyboardManager keyboardManager) {
            if (shortcut == null) {
                return Optional.empty();
            }

            List<String> maskKeyNames = new ArrayList<>();
            List<String> nonMaskKeyNames = new ArrayList<>();

            if (shortcut.masks != null) {
                for (KeyElement mask : shortcut.masks) {
                    if (mask.keyname == null) {
                        throw new IllegalArgumentException("Shortcut mask keyname cannot be null");
                    }
                    maskKeyNames.add(mask.keyname);
                }
            }

            if (shortcut.keys != null) {
                for (KeyElement key : shortcut.keys) {
                    if (key.keyname == null) {
                        throw new IllegalArgumentException("Shortcut key keyname cannot be null");
                    }
                    nonMaskKeyNames.add(key.keyname);
                }
            }

            // Fail fast if shortcut has no keys
            if (nonMaskKeyNames.isEmpty()) {
                throw new IllegalArgumentException("Shortcut must have at least one key");
            }

            // Create shortcut using the injected KeyboardManager
            return Optional.of(
                    Shortcut.forPlatform(
                            createKeyStrokeFromXmlForm(
                                    maskKeyNames, nonMaskKeyNames, keyboardManager),
                            keyboardManager));
        }

        private KeyStroke createKeyStrokeFromXmlForm(
                List<String> maskKeyNames,
                List<String> nonMaskKeyNames,
                KeyboardManager keyboardManager) {
            String internalShortcutForm =
                    Stream.concat(
                                    maskKeyNames.stream()
                                            .map(keyboardManager::xmlKeynameToInternalForm),
                                    nonMaskKeyNames.stream()
                                            .map(keyboardManager::xmlKeynameToInternalForm))
                            .collect(joining(" "));

            KeyStroke stroke = KeyStroke.getKeyStroke(internalShortcutForm);
            if (stroke == null) {
                throw new IllegalArgumentException(
                        "Cannot parse keystroke: " + internalShortcutForm);
            }
            return stroke;
        }

        private Set<Platform.PlatformType> parseCompatiblePlatforms(String osSpec) {
            if (osSpec == null || osSpec.trim().isEmpty()) {
                return Set.of(); // Empty set means compatible with all platforms
            }

            Set<Platform.PlatformType> platforms = EnumSet.noneOf(Platform.PlatformType.class);
            String[] osNames = osSpec.split(",");

            for (String osName : osNames) {
                String trimmed = osName.trim();
                switch (trimmed) {
                    case "Mac OS X" -> platforms.add(Platform.PlatformType.MACOS);
                    case "Windows" -> platforms.add(Platform.PlatformType.WINDOWS);
                    case "Linux" -> platforms.add(Platform.PlatformType.LINUX);
                    default -> throw new IllegalArgumentException("Unknown OS name: " + trimmed);
                }
            }

            return platforms;
        }
    }

    /** Shortcut element containing mask and key information. */
    public static final class ShortcutElement {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JsonProperty("mask")
        private final List<KeyElement> masks;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JsonProperty("key")
        private final List<KeyElement> keys;

        // Jackson requires default constructor
        private ShortcutElement() {
            this.masks = new ArrayList<>();
            this.keys = new ArrayList<>();
        }

        public ShortcutElement(List<KeyElement> masks, List<KeyElement> keys) {
            this.masks = masks != null ? new ArrayList<>(masks) : new ArrayList<>();
            this.keys = keys != null ? new ArrayList<>(keys) : new ArrayList<>();

            // Validate that all key elements have non-null keynames
            for (KeyElement mask : this.masks) {
                if (mask.keyname == null) {
                    throw new IllegalArgumentException(
                            "ShortcutElement mask keyname cannot be null");
                }
            }
            for (KeyElement key : this.keys) {
                if (key.keyname == null) {
                    throw new IllegalArgumentException(
                            "ShortcutElement key keyname cannot be null");
                }
            }
        }
    }

    /** Key element representing a single key or mask. */
    public static final class KeyElement {
        @JacksonXmlProperty(isAttribute = true)
        private final String keyname;

        // Jackson requires default constructor
        private KeyElement() {
            this.keyname = null;
        }

        public KeyElement(String keyname) {
            if (keyname == null) {
                throw new IllegalArgumentException("KeyElement keyname cannot be null");
            }
            this.keyname = keyname;
        }
    }

    /**
     * Immutable configuration for a single action. Guarantees className and name are never null or
     * empty.
     */
    @Getter
    public static final class ActionConfig {
        private final String className;
        private final String name;
        private final Optional<String> tooltip;
        private final Optional<String> enumValue;
        private final Optional<Shortcut> shortcut;

        public ActionConfig(
                String className,
                String name,
                String tooltip,
                String enumValue,
                Optional<Shortcut> shortcut) {
            this.className = requireValidString(className, "className");
            this.name = requireValidString(name, "name");
            this.tooltip = Optional.ofNullable(tooltip).map(String::trim).filter(s -> !s.isEmpty());
            this.enumValue =
                    Optional.ofNullable(enumValue).map(String::trim).filter(s -> !s.isEmpty());
            this.shortcut = shortcut;
        }

        private static String requireValidString(String value, String fieldName) {
            return switch (value) {
                case null -> throw new IllegalArgumentException(fieldName + " cannot be null");
                case String s when s.trim().isEmpty() ->
                        throw new IllegalArgumentException(fieldName + " cannot be empty");
                default -> value;
            };
        }

        @Override
        public String toString() {
            return "ActionConfig{"
                    + "className='"
                    + className
                    + '\''
                    + ", name='"
                    + name
                    + '\''
                    + ", tooltip="
                    + tooltip
                    + ", enumValue="
                    + enumValue
                    + ", shortcut="
                    + shortcut
                    + '}';
        }
    }

    /** Exception thrown when action parsing fails. */
    public static class ActionParseException extends Exception {
        public ActionParseException(String message) {
            super(message);
        }

        public ActionParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
