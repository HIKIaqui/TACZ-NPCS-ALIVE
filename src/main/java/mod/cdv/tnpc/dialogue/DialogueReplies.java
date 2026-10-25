package mod.cdv.tnpc.dialogue;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Reloaded atomically together with the authored dialogue profiles. */
public final class DialogueReplies {
    public record Lines(List<String> activity, List<String> news, List<String> help, List<String> follow) {
        public static final Lines EMPTY = new Lines(List.of(), List.of(), List.of(), List.of());
        Lines inherit(Lines fallback) {
            return new Lines(activity.isEmpty() ? fallback.activity : activity,
                news.isEmpty() ? fallback.news : news, help.isEmpty() ? fallback.help : help,
                follow.isEmpty() ? fallback.follow : follow);
        }
    }
    public record Settings(Lines defaults, Map<String, Lines> loadouts) {
        public Lines forLoadout(String id) { return loadouts.getOrDefault(id, Lines.EMPTY).inherit(defaults); }
    }
    public static final String DEFAULT_JSON = """
        {
          "defaults": {"activity": [], "news": [], "help": [], "follow": []},
          "loadouts": {
            "villagers": {"activity": ["I'm gathering supplies. We need to keep the village going."]},
            "illagers": {"activity": ["Keeping watch. That's all you need to know."]}
          }
        }
        """;
    private DialogueReplies() {}
    public static Settings read(Path path) throws IOException {
        if (Files.notExists(path)) Files.writeString(path, DEFAULT_JSON);
        return parse(Files.readString(path));
    }
    public static Settings parse(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        keys(root, Set.of("defaults", "loadouts"));
        Lines defaults = lines(object(root, "defaults"));
        Map<String, Lines> loadouts = new HashMap<>();
        for (var entry : object(root, "loadouts").entrySet()) {
            if (entry.getKey().isBlank()) throw new IllegalArgumentException("Blank reply loadout");
            loadouts.put(entry.getKey(), lines(entry.getValue().getAsJsonObject()));
        }
        return new Settings(defaults, Map.copyOf(loadouts));
    }
    private static Lines lines(JsonObject o) {
        keys(o, Set.of("activity", "news", "help", "follow"));
        return new Lines(texts(o, "activity"), texts(o, "news"), texts(o, "help"), texts(o, "follow"));
    }
    private static List<String> texts(JsonObject o, String key) {
        if (!o.has(key)) return List.of();
        JsonElement value = o.get(key);
        JsonArray values;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            values = new JsonArray(); values.add(value);
        } else values = value.getAsJsonArray();
        if (values.size() > 128) throw new IllegalArgumentException("Too many replies: " + key);
        List<String> result = new ArrayList<>();
        for (var line : values) {
            if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString()
                || line.getAsString().isBlank() || line.getAsString().length() > 2048)
                throw new IllegalArgumentException("Invalid reply: " + key);
            result.add(line.getAsString());
        }
        return List.copyOf(result);
    }
    private static JsonObject object(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonObject(key) : new JsonObject();
    }
    private static void keys(JsonObject o, Set<String> allowed) {
        for (String key : o.keySet()) if (!allowed.contains(key))
            throw new IllegalArgumentException("Unknown dialogue reply option: " + key);
    }
}
