package mod.cdv.tnpc.dialogue;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import net.aviel.dialogue.npc.dialogue.DialogueCondition;
import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;

/** Parses a complete candidate before anything is published to ADM. No game state here. */
public final class DialogueProfiles {
    public record Rule(String id, int priority, DialogueCondition when, String dialogue,
                       String genericMode, NpcDialogueDefinition specific) {}
    public record Snapshot(boolean enabled, double maxDistance, int damageCooldownTicks,
                           int maxDurationTicks, String genericDialogue, boolean genericWhenUnmatched,
                           List<Rule> rules, Map<String, String> dialogues) {
        public String select(Predicate<DialogueCondition> matches, Predicate<NpcDialogueDefinition.Choice> available) {
            for (Rule rule : rules) {
                if (!matches.test(rule.when())) continue;
                // Fallback applies when the specific greeting has no available topic.
                if (rule.genericMode().equals("fallback") && !hasTopics(rule.specific(), available))
                    return genericDialogue;
                return rule.dialogue();
            }
            return genericWhenUnmatched ? genericDialogue : null;
        }
    }
    private DialogueProfiles() {}

    public static Snapshot read(Path config) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        ReputationDialogue.validate(root);
        Path folder = config.resolveSibling("tacznpc_dialogues");
        String generic = string(root, "genericProfile", "generic");
        Map<String, JsonObject> profiles = new HashMap<>();
        JsonObject genericJson = readProfile(folder, generic, profiles);
        Map<String, String> dialogues = new LinkedHashMap<>();
        String genericId = "tacznpcs:conversation/generic";
        dialogues.put(genericId, genericJson.toString());
        List<Rule> rules = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        JsonArray entries = root.has("rules") ? root.getAsJsonArray("rules") : new JsonArray();
        for (JsonElement element : entries) {
            JsonObject rule = element.getAsJsonObject();
            String id = string(rule, "id", "");
            if (id.length() > 96 || !id.matches("[a-z0-9_/-]+") || !ids.add(id))
                throw new IllegalArgumentException("Invalid or duplicate rule id: " + id);
            if (!bool(rule, "enabled", true)) continue;
            String mode = string(rule, "genericMode", "append");
            if (!Set.of("append", "fallback", "disabled").contains(mode))
                throw new IllegalArgumentException("Unknown genericMode: " + mode);
            JsonObject specific = readProfile(folder, string(rule, "profile", ""), profiles);
            JsonObject compiled = mode.equals("append") ? append(specific, genericJson) : specific;
            String reference = "tacznpcs:conversation/rule/" + id;
            dialogues.put(reference, compiled.toString());
            JsonObject conditionWrapper = new JsonObject();
            if (rule.has("when")) conditionWrapper.add("condition", rule.get("when"));
            rules.add(new Rule(id, integer(rule, "priority", 0), DialogueCondition.read(conditionWrapper),
                reference, mode, NpcDialogueDefinition.fromJson(specific.toString())));
        }
        // Stable sort: ties retain file order, independent of faction Set iteration order.
        rules.sort(Comparator.comparingInt(Rule::priority).reversed());
        double distance = number(root, "maxDistance", 5);
        int cooldown = integer(root, "damageCooldownTicks", 100);
        int duration = integer(root, "maxDurationTicks", 2400);
        if (!Double.isFinite(distance) || distance < 1 || distance > 16 || cooldown < 0 || cooldown > 12000
            || duration < 20 || duration > 72000)
            throw new IllegalArgumentException("Dialogue distance/cooldown/duration out of range");
        dialogues.values().forEach(NpcDialogueDefinition::fromJson);
        return new Snapshot(bool(root, "enabled", true), distance, cooldown, duration,
            genericId, bool(root, "genericWhenUnmatched", true), List.copyOf(rules), Map.copyOf(dialogues));
    }

    private static JsonObject readProfile(Path folder, String name, Map<String, JsonObject> cache) throws IOException {
        if (!name.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid profile name: " + name);
        if (cache.containsKey(name)) return cache.get(name);
        JsonObject json = JsonParser.parseString(Files.readString(folder.resolve(name + ".json"))).getAsJsonObject();
        // A fixed greeting makes topic composition and fallback unambiguous.
        if (json.has("random_start")) throw new IllegalArgumentException("Use rules for conditional greetings, not random_start");
        String start = string(json, "start", "");
        if (start.isBlank() || !json.getAsJsonObject("nodes").has(start))
            throw new IllegalArgumentException("Profile requires an existing start node: " + name);
        ReputationDialogue.validate(json);
        NpcDialogueDefinition.fromJson(json.toString());
        cache.put(name, json);
        return json;
    }

    private static boolean hasTopics(NpcDialogueDefinition profile, Predicate<NpcDialogueDefinition.Choice> available) {
        return profile.node(profile.startNodes().getFirst()).choices().stream()
            .anyMatch(choice -> !choice.close() && available.test(choice));
    }

    /** Prefix every node and next edge so two independently authored profiles cannot collide. */
    public static JsonObject append(JsonObject specific, JsonObject generic) {
        JsonObject result = specific.deepCopy();
        JsonObject nodes = result.getAsJsonObject("nodes");
        String genericStart = generic.get("start").getAsString();
        Map<String, String> remap = new HashMap<>();
        int index = 0;
        for (String key : generic.getAsJsonObject("nodes").keySet()) {
            String id;
            do { id = "tnpc_generic_" + index++; } while (nodes.has(id));
            remap.put(key, id);
        }
        for (var entry : generic.getAsJsonObject("nodes").entrySet()) {
            JsonObject node = entry.getValue().getAsJsonObject().deepCopy();
            if (node.has("choices")) for (JsonElement element : node.getAsJsonArray("choices")) {
                JsonObject choice = element.getAsJsonObject();
                if (choice.has("next") && !choice.get("next").getAsString().isBlank()) {
                    String target = remap.get(choice.get("next").getAsString());
                    if (target == null) throw new IllegalArgumentException("Generic choice points to a missing node");
                    choice.addProperty("next", target);
                }
            }
            nodes.add(remap.get(entry.getKey()), node);
        }
        JsonObject root = nodes.getAsJsonObject(result.get("start").getAsString());
        JsonArray choices = root.has("choices") ? root.getAsJsonArray("choices") : new JsonArray();
        JsonArray merged = new JsonArray();
        for (JsonElement choice : choices) if (!bool(choice.getAsJsonObject(), "close", false)) merged.add(choice);
        JsonObject genericRoot = nodes.getAsJsonObject(remap.get(genericStart));
        if (genericRoot.has("choices")) for (JsonElement choice : genericRoot.getAsJsonArray("choices"))
            if (!bool(choice.getAsJsonObject(), "close", false)) merged.add(choice.deepCopy());
        boolean hasClose = false;
        for (JsonElement choice : choices) if (bool(choice.getAsJsonObject(), "close", false)) { merged.add(choice); hasClose = true; }
        if (!hasClose) {
            JsonObject close = new JsonObject(); close.addProperty("text", "Goodbye."); close.addProperty("close", true); merged.add(close);
        }
        root.add("choices", merged);
        return result;
    }
    private static String string(JsonObject o, String key, String value) { return o.has(key) ? o.get(key).getAsString() : value; }
    private static boolean bool(JsonObject o, String key, boolean value) { return o.has(key) ? o.get(key).getAsBoolean() : value; }
    private static int integer(JsonObject o, String key, int value) {
        if (!o.has(key)) return value;
        double n = o.get(key).getAsDouble();
        if (!Double.isFinite(n) || n != Math.rint(n) || n < Integer.MIN_VALUE || n > Integer.MAX_VALUE)
            throw new IllegalArgumentException(key + " must be an integer");
        return (int)n;
    }
    private static double number(JsonObject o, String key, double value) { return o.has(key) ? o.get(key).getAsDouble() : value; }
}
