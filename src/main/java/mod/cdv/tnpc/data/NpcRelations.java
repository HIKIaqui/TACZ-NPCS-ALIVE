package mod.cdv.tnpc.data;

import com.google.gson.*;
import java.util.*;
import mod.cdv.tnpc.reputation.ReputationConfig;
import net.minecraft.world.entity.EntityType;

/** Directed relationships. Positive values describe an opinion, never faction membership. */
public final class NpcRelations {
    public record Definition(String faction, Map<String,Integer> values) {}
    private NpcRelations() {}
    public static Definition read(String loadout, JsonObject object) {
        ReputationConfig.id(loadout);
        if (object.has("aligned") || object.has("opposed"))
            throw new IllegalArgumentException("Loadout " + loadout + ": aligned/opposed were removed; use faction and relations");
        String faction = loadout;
        if (object.has("faction")) {
            var f = object.get("faction");
            if (!f.isJsonPrimitive() || !f.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("faction must be a string");
            faction = f.getAsString();
        }
        ReputationConfig.id(faction);
        Map<String,Integer> values = new TreeMap<>();
        if (object.has("relations")) for (var e : object.getAsJsonObject("relations").entrySet()) {
            if (e.getKey().equals("players")) throw new IllegalArgumentException("Use tacznpc_reputation.json factionDefaults for initial player reputation, not relations.players");
            ReputationConfig.id(e.getKey()); values.put(e.getKey(), ReputationConfig.score(e.getValue()));
        }
        return new Definition(faction, Map.copyOf(values));
    }
    public static long value(String firstId, NPCTemplate first, String secondId, NPCTemplate second, Set<String> ignoredGroups) {
        if (first == null || second == null) return 0;
        // NPCs have an explicit identity; do not identify all NPCs by their shared entity type/category.
        return first.relations().getOrDefault(secondId, first.relations().getOrDefault(second.faction(), 0));
    }
    public static long mobValue(NPCTemplate first, Set<String> groups, String category) {
        // Specific configured entity factions override broad vanilla categories. Conflicts use the lowest value.
        var matching = groups.stream().filter(g -> !g.equals(category) && first.relations().containsKey(g))
            .mapToInt(g -> first.relations().get(g)).min();
        return matching.isPresent() ? matching.getAsInt() : first.relations().getOrDefault(category,0);
    }
    public static boolean opposed(String a,NPCTemplate first,String b,NPCTemplate second) { return opposed(a,first,b,second,Set.of()); }
    public static boolean opposed(String a,NPCTemplate first,String b,NPCTemplate second,Set<String> groups) {
        return first != null && second != null && ReputationConfig.settings().hostile(value(a,first,b,second,groups));
    }
    public static boolean allied(String a,NPCTemplate first,String b,NPCTemplate second) { return allied(a,first,b,second,Set.of()); }
    public static boolean allied(String a,NPCTemplate first,String b,NPCTemplate second,Set<String> groups) {
        return first != null && second != null && ReputationConfig.settings().allied(value(a,first,b,second,groups));
    }
    public static List<String> alliedLoadouts(String id,NPCTemplate actor,Map<String,NPCTemplate> templates) {
        return alliedLoadouts(id,actor,templates,Set.of());
    }
    public static List<String> alliedLoadouts(String id,NPCTemplate actor,Map<String,NPCTemplate> templates,Set<String> groups) {
        return templates.entrySet().stream().filter(e -> allied(id,actor,e.getKey(),e.getValue()) && allied(e.getKey(),e.getValue(),id,actor))
            .map(Map.Entry::getKey).sorted().toList();
    }
    public static Set<String> entityGroups(EntityType<?> type,Map<String,Set<EntityType<?>>> factions) {
        Set<String> groups = new HashSet<>(); groups.add(type.getCategory().getName());
        factions.forEach((id,types) -> { if (types.contains(type)) groups.add(id); }); return groups;
    }
}
