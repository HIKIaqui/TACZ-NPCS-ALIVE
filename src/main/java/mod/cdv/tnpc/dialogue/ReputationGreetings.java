package mod.cdv.tnpc.dialogue;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.IntUnaryOperator;
import mod.cdv.tnpc.reputation.ReputationConfig;

/** Immutable greeting alternatives; a single selected line belongs to one conversation snapshot. */
public final class ReputationGreetings {
    public static final Set<String> TIERS = Set.of("villainized","hostile","hated","neutral","friendly","allied","idolized");
    public record Settings(boolean enabled, Map<String,List<String>> defaults,
                           Map<String,Map<String,List<String>>> factions,
                           Map<String,Map<String,List<String>>> loadouts) {
        public List<String> lines(String loadout,String faction,long effective) {
            if (!enabled) return List.of();
            String tier=ReputationConfig.tier(effective);
            for (var source : List.of(loadouts.getOrDefault(loadout,Map.of()), factions.getOrDefault(faction,Map.of()), defaults)) {
                var lines=source.getOrDefault(tier,List.of()); if (!lines.isEmpty()) return lines;
            }
            return List.of();
        }
        public String select(String loadout,String faction,long effective,IntUnaryOperator random) {
            var lines=lines(loadout,faction,effective); return lines.isEmpty() ? null : lines.get(random.applyAsInt(lines.size()));
        }
        public String longest() {
            if (!enabled) return null;
            return java.util.stream.Stream.concat(java.util.stream.Stream.of(defaults),
                java.util.stream.Stream.concat(factions.values().stream(),loadouts.values().stream()))
                .flatMap(m -> m.values().stream()).flatMap(List::stream).max(Comparator.comparingInt(String::length)).orElse(null);
        }
    }
    public static final String DEFAULT_JSON = """
        {
          "enabled": true,
          "defaults": {
            "villainized": ["After everything you've done? Get out of my sight."],
            "hostile": ["You have a lot of nerve showing up here."],
            "hated": ["Make it quick. I'm not in the mood.", "What do you want this time?"],
            "neutral": [],
            "friendly": ["Good to see you again. Need something?", "Hey, glad you're still in one piece."],
            "allied": ["There you are, friend. What can I do for you?", "Always good to have you around."],
            "idolized": ["For you? I'll always make time.", "If anyone can get us through this, it's you."]
          },
          "factions": {},
          "loadouts": {}
        }
        """;
    private ReputationGreetings() {}
    public static Settings read(Path path) throws IOException {
        if (Files.notExists(path)) Files.writeString(path,DEFAULT_JSON);
        return parse(Files.readString(path));
    }
    public static Settings parse(String json) {
        var root=JsonParser.parseString(json).getAsJsonObject();
        ReputationConfig.keys(root,Set.of("enabled","defaults","factions","loadouts"));
        boolean enabled=true;
        if (root.has("enabled")) {
            var e=root.get("enabled"); if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Greeting enabled must be boolean");
            enabled=e.getAsBoolean();
        }
        return new Settings(enabled,tiers(object(root,"defaults")),groups(object(root,"factions")),groups(object(root,"loadouts")));
    }
    private static Map<String,Map<String,List<String>>> groups(JsonObject o) {
        Map<String,Map<String,List<String>>> result=new TreeMap<>();
        for (var e:o.entrySet()) { ReputationConfig.id(e.getKey()); result.put(e.getKey(),tiers(e.getValue().getAsJsonObject())); }
        return Map.copyOf(result);
    }
    private static Map<String,List<String>> tiers(JsonObject o) {
        Map<String,List<String>> result=new TreeMap<>();
        for (var e:o.entrySet()) {
            String tier=ReputationConfig.canonicalTier(e.getKey());
            if(!TIERS.contains(tier) || result.containsKey(tier)) throw new IllegalArgumentException("Unknown/duplicate greeting tier: "+e.getKey());
            var value=e.getValue(); List<JsonElement> values=value.isJsonArray() ? value.getAsJsonArray().asList() : List.of(value);
            if (values.size()>128) throw new IllegalArgumentException("Too many greetings: "+e.getKey());
            List<String> lines=new ArrayList<>();
            for (var line:values) {
                if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString() || line.getAsString().isBlank() || line.getAsString().length()>2048)
                    throw new IllegalArgumentException("Invalid greeting: "+e.getKey());
                lines.add(line.getAsString());
            }
            result.put(tier,List.copyOf(lines));
        }
        return Map.copyOf(result);
    }
    private static JsonObject object(JsonObject o,String key) { return o.has(key) ? o.getAsJsonObject(key) : new JsonObject(); }
}
