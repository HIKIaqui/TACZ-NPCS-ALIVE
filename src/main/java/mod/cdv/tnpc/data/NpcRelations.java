package mod.cdv.tnpc.data;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;
import net.minecraft.world.entity.EntityType;

/** Direct NPC relationships. Alliances are symmetric, never transitive. */
public final class NpcRelations {
    private NpcRelations() {}

    public static boolean opposed(String firstId, NPCTemplate first, String secondId, NPCTemplate second) {
        return opposed(firstId, first, secondId, second, Set.of());
    }

    public static boolean opposed(String firstId, NPCTemplate first, String secondId, NPCTemplate second,
                                  Set<String> entityGroups) {
        if (first == null || second == null) return false;
        return first.opposed().contains(secondId) || second.opposed().contains(firstId)
            || !Collections.disjoint(first.opposed(), second.aligned())
            || !Collections.disjoint(second.opposed(), first.aligned())
            || matchesGroup(first.opposed(), entityGroups) || matchesGroup(second.opposed(), entityGroups);
    }

    public static boolean allied(String firstId, NPCTemplate first, String secondId, NPCTemplate second) {
        return allied(firstId, first, secondId, second, Set.of());
    }

    public static boolean allied(String firstId, NPCTemplate first, String secondId, NPCTemplate second,
                                 Set<String> entityGroups) {
        if (first == null || second == null || opposed(firstId, first, secondId, second, entityGroups)) return false;
        return first.aligned().contains(secondId) || second.aligned().contains(firstId)
            || !Collections.disjoint(first.aligned(), second.aligned())
            || matchesGroup(first.aligned(), entityGroups) || matchesGroup(second.aligned(), entityGroups);
    }

    /** Registered loadouts only: deleted/unknown IDs cannot be spawned by an encounter. */
    public static List<String> alliedLoadouts(String actorId, NPCTemplate actor, Map<String, NPCTemplate> templates) {
        return alliedLoadouts(actorId, actor, templates, Set.of());
    }

    public static List<String> alliedLoadouts(String actorId, NPCTemplate actor, Map<String, NPCTemplate> templates,
                                            Set<String> entityGroups) {
        return templates.entrySet().stream()
            .filter(entry -> allied(actorId, actor, entry.getKey(), entry.getValue(), entityGroups))
            .map(Map.Entry::getKey).sorted().toList();
    }

    /** NPCs share one entity type, including its category and configured type-based factions. */
    public static Set<String> entityGroups(EntityType<?> type, Map<String, Set<EntityType<?>>> factions) {
        Set<String> groups = new HashSet<>();
        groups.add(type.getCategory().getName());
        factions.forEach((id, types) -> { if (types.contains(type)) groups.add(id); });
        return groups;
    }

    private static boolean matchesGroup(Set<String> references, Set<String> groups) {
        // Mob categories were historically case-insensitive; faction/loadout IDs remain exact.
        return references.stream().anyMatch(id -> groups.contains(id)
            || (isCategory(id) && groups.contains(id.toLowerCase(Locale.ROOT))));
    }

    private static boolean isCategory(String id) {
        return switch (id.toUpperCase(Locale.ROOT)) {
            case "MONSTER", "CREATURE", "AMBIENT", "AXOLOTLS", "UNDERGROUND_WATER_CREATURE",
                 "WATER_CREATURE", "WATER_AMBIENT", "MISC" -> true;
            default -> false;
        };
    }
}
