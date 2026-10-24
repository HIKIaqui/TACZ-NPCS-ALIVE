import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import mod.cdv.tnpc.dialogue.DialogueProfiles;
import net.aviel.dialogue.npc.dialogue.*;

public class DialogueConfigSmokeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("tnpc-dialogue-validation-");
        Path profiles = Files.createDirectory(directory.resolve("tacznpc_dialogues"));
        Path config = directory.resolve("tacznpc_dialogue.json");
        for (String file : List.of("generic.json", "example_faction.json"))
            Files.copy(Path.of("src/main/resources/dialogue_defaults", file), profiles.resolve(file));
        Files.copy(Path.of("src/main/resources/dialogue_defaults/tacznpc_dialogue.json"), config);
        var defaults = DialogueProfiles.read(config);
        check(defaults.select(c -> true, c -> true).equals("tacznpcs:conversation/generic"), "Generic available by default");
        JsonObject root = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        JsonArray rules = new JsonArray();
        rules.add(rule("faction", 10, "append", "faction"));
        rules.add(rule("loadout", 100, "disabled", "loadout"));
        root.add("rules", rules); Files.writeString(config, root.toString());
        var snapshot = DialogueProfiles.read(config);
        check(snapshot.select(c -> true, c -> true).endsWith("rule/loadout"), "Highest priority wins despite file order");
        check(snapshot.select(c -> c instanceof DialogueCondition.Predicate p && p.id().equals("faction"), c -> true).endsWith("rule/faction"), "Faction rule selected");
        JsonObject merged = JsonParser.parseString(snapshot.dialogues().get("tacznpcs:conversation/rule/faction")).getAsJsonObject();
        var definition = NpcDialogueDefinition.fromJson(merged.toString());
        var greeting = definition.node("greeting");
        check(greeting.choices().size() == 4, "Faction topic plus two generic topics plus one goodbye");
        check(definition.node("faction") != null, "Specific node retained");
        for (var node : merged.getAsJsonObject("nodes").entrySet()) {
            JsonObject object = node.getValue().getAsJsonObject();
            for (JsonElement e : object.getAsJsonArray("choices")) {
                JsonObject choice = e.getAsJsonObject();
                if (choice.has("next")) check(definition.node(choice.get("next").getAsString()) != null, "Merged edge resolves");
            }
        }
        JsonObject unmerged = JsonParser.parseString(snapshot.dialogues().get("tacznpcs:conversation/rule/loadout")).getAsJsonObject();
        check(unmerged.getAsJsonObject("nodes").size() == 2, "Disabled generic mode retains only specific nodes");
        rules = new JsonArray(); rules.add(rule("conditional", 1, "fallback", "conditional")); root.add("rules", rules);
        Files.writeString(config, root.toString()); snapshot = DialogueProfiles.read(config);
        check(snapshot.select(c -> true, c -> false).endsWith("/generic"), "Unavailable specific topics fall back to generic");
        check(snapshot.select(c -> true, c -> true).endsWith("rule/conditional"), "Available specific topics suppress fallback");
        root.addProperty("genericWhenUnmatched", false); Files.writeString(config, root.toString());
        check(DialogueProfiles.read(config).select(c -> false, c -> true) == null, "Generic disabled when unmatched");
        rules.add(rule("conditional", 2, "append", "x")); Files.writeString(config, root.toString());
        rejects(config, "Duplicate rule rejected");
        rules = new JsonArray(); rules.add(rule("bad", 1, "typo", "x")); root.add("rules", rules); Files.writeString(config, root.toString());
        rejects(config, "Invalid generic mode rejected");
        rules = new JsonArray(); JsonObject traversal = rule("bad", 1, "append", "x"); traversal.addProperty("profile", "../generic"); rules.add(traversal); root.add("rules", rules); Files.writeString(config, root.toString());
        rejects(config, "Profile path traversal rejected");
        root.add("rules", new JsonArray()); root.addProperty("maxDistance", 0); Files.writeString(config, root.toString());
        rejects(config, "Invalid distance rejected");
        root.addProperty("maxDistance", 5); root.addProperty("damageCooldownTicks", 2.5); Files.writeString(config, root.toString());
        rejects(config, "Fractional ticks rejected");
        try (var paths = Files.walk(directory)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        System.out.println("PASS: " + checks + " dialogue config/routing/merge checks");
    }
    private static JsonObject rule(String id, int priority, String mode, String condition) {
        JsonObject rule = new JsonObject(); rule.addProperty("id", id); rule.addProperty("priority", priority);
        rule.addProperty("profile", "example_faction"); rule.addProperty("genericMode", mode);
        JsonObject when = new JsonObject(); when.addProperty("type", "tnpc.faction"); when.addProperty("id", condition); rule.add("when", when);
        return rule;
    }
    private static void rejects(Path config, String message) throws Exception {
        try { DialogueProfiles.read(config); throw new AssertionError(message); }
        catch (IllegalArgumentException | IllegalStateException expected) { checks++; }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
}
