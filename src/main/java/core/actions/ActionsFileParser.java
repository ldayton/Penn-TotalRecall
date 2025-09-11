package core.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;

@Singleton
public class ActionsFileParser {

    private final ObjectMapper mapper;

    @Inject
    public ActionsFileParser(@NonNull ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<ActionConfig> parseActions(@NonNull InputStream inputStream) throws Exception {
        List<ActionConfig> configs = new ArrayList<>();
        JsonNode root = mapper.readTree(inputStream);
        JsonNode actions = root.get("actions");

        for (JsonNode action : actions) {
            String className = action.get("class").asText();
            String name = action.get("name").asText();

            Optional<String> tooltip =
                    action.has("tooltip")
                            ? Optional.of(action.get("tooltip").asText())
                            : Optional.empty();

            Optional<ShortcutSpec> shortcut = Optional.empty();
            if (action.has("shortcut")) {
                JsonNode shortcutNode = action.get("shortcut");
                Set<String> modifiers = new HashSet<>();
                if (shortcutNode.has("modifiers")) {
                    for (JsonNode modifier : shortcutNode.get("modifiers")) {
                        modifiers.add(modifier.asText());
                    }
                }
                String key = shortcutNode.get("key").asText();
                shortcut = Optional.of(new ShortcutSpec(modifiers, key));
            }

            configs.add(new ActionConfig(className, name, tooltip, shortcut));
        }

        return configs;
    }
}
