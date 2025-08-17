package actions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import env.Platform;
import jakarta.inject.Inject;
import lombok.NonNull;

/**
 * Jackson-based parser for actions.xml configuration files.
 */
public class ActionsFileParser {
    private static final Logger logger = LoggerFactory.getLogger(ActionsFileParser.class);
    
    private final XmlMapper xmlMapper;
    private final Platform platform;
    
    @Inject
    public ActionsFileParser(@NonNull Platform platform) {
        this.xmlMapper = createConfiguredMapper();
        this.platform = platform;
    }
    
    /**
     * Parses actions.xml from the given URL.
     */
    public List<ActionConfig> parseActions(@NonNull URL url) throws ActionParseException {
        logger.debug("Parsing actions from: {}", url);
        
        try (InputStream inputStream = url.openStream()) {
            ActionsDocument document = xmlMapper.readValue(inputStream, ActionsDocument.class);
            return document.toActionConfigs(platform);
        } catch (IOException e) {
            throw new ActionParseException("Failed to parse actions.xml from " + url, e);
        }
    }
    
    /**
     * Parses actions.xml from the classpath.
     */
    public List<ActionConfig> parseActionsFromClasspath(@NonNull String resourcePath) throws ActionParseException {
        URL resource = getClass().getResource(resourcePath);
        if (resource == null) {
            throw new ActionParseException("Resource not found: " + resourcePath);
        }
        return parseActions(resource);
    }
    
    /**
     * Creates a properly configured XmlMapper following Jackson best practices.
     */
    private XmlMapper createConfiguredMapper() {
        XmlMapper mapper = new XmlMapper();
        
        // Configure for lenient parsing
        mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        
        return mapper;
    }
    
    /**
     * Root document structure for actions.xml.
     */
    @JacksonXmlRootElement(localName = "actions")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ActionsDocument {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JsonProperty("action")
        private final List<ActionElement> actions;
        
        // Jackson requires default constructor
        private ActionsDocument() {
            this.actions = new ArrayList<>();
        }
        
        public ActionsDocument(@JsonProperty("action") List<ActionElement> actions) {
            this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
        }
        
        public List<ActionConfig> toActionConfigs(Platform platform) {
            return actions.stream()
                    .map(element -> element.toActionConfig(platform))
                    .flatMap(Optional::stream)
                    .toList();
        }
    }
    
    /**
     * Individual action element from XML.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
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
        
        // Jackson requires default constructor
        private ActionElement() {
            this.className = null;
            this.name = null;
            this.tooltip = null;
            this.enumValue = null;
            this.os = null;
        }
        
        public ActionElement(
                @JsonProperty("class") String className,
                @JsonProperty("name") String name,
                @JsonProperty("tooltip") String tooltip,
                @JsonProperty("enum") String enumValue,
                @JsonProperty("os") String os) {
            this.className = className;
            this.name = name;
            this.tooltip = tooltip;
            this.enumValue = enumValue;
            this.os = os;
        }
        
        /**
         * Converts this element to an ActionConfig, applying OS filtering.
         */
        public Optional<ActionConfig> toActionConfig(Platform platform) {
            // Check OS compatibility first
            Set<Platform.PlatformType> compatiblePlatforms = parseCompatiblePlatforms(os);
            if (!compatiblePlatforms.isEmpty() && !compatiblePlatforms.contains(platform.detect())) {
                logger.debug("Skipping action {} - not compatible with current OS", className);
                return Optional.empty();
            }
            
            try {
                // ActionConfig constructor will validate required fields
                return Optional.of(new ActionConfig(className, name, tooltip, enumValue));
            } catch (IllegalArgumentException e) {
                logger.warn("Skipping invalid action: {}", e.getMessage());
                return Optional.empty();
            }
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
                    default -> logger.warn("Unknown OS name in actions.xml: {}", trimmed);
                }
            }
            
            return platforms;
        }
    }
    
    /**
     * Immutable configuration for a single action.
     * Guarantees className and name are never null or empty.
     */
    @Getter
    public static final class ActionConfig {
        private final String className;
        private final String name;
        private final Optional<String> tooltip;
        private final Optional<String> enumValue;
        
        public ActionConfig(String className, String name, String tooltip, String enumValue) {
            this.className = requireValidString(className, "className");
            this.name = requireValidString(name, "name");
            this.tooltip = Optional.ofNullable(tooltip);
            this.enumValue = Optional.ofNullable(enumValue);
        }
        
        private static String requireValidString(String value, String fieldName) {
            return switch (value) {
                case null -> throw new IllegalArgumentException(fieldName + " cannot be null");
                case String s when s.trim().isEmpty() -> throw new IllegalArgumentException(fieldName + " cannot be empty");
                default -> value;
            };
        }

        @Override
        public String toString() {
            return "ActionConfig{" +
                    "className='" + className + '\'' +
                    ", name='" + name + '\'' +
                    ", tooltip=" + tooltip +
                    ", enumValue=" + enumValue +
                    '}';
        }
    }
    
    /**
     * Exception thrown when action parsing fails.
     */
    public static class ActionParseException extends Exception {
        public ActionParseException(String message) {
            super(message);
        }
        
        public ActionParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}