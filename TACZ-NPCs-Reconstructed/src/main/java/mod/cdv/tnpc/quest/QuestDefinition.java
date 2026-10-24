package mod.cdv.tnpc.quest;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record QuestDefinition(String id, boolean enabled, int weight, int xp, Set<String> loadouts,
    Set<String> excludedLoadouts, Set<String> dimensions, boolean requireOutsideObjective,
    LocationRules acceptance, Objective objective, Dialogues dialogues) {

    public enum Type { BIOME, STRUCTURE, POI }
    public record LocationRules(Set<String> excludedBiomes, Set<String> excludedStructures,
                                Set<String> excludedPoiTypes) {
        public boolean allows(QuestLocation.Snapshot place, int radius) {
            return !excludedBiomes.contains(place.biome())
                && java.util.Collections.disjoint(excludedStructures, place.structures())
                && excludedPoiTypes.stream().noneMatch(id -> place.hasPoi(id, radius));
        }
    }
    public record Objective(Type type, Set<String> targets, int poiRadius, double minTravelDistance,
                            LocationRules exclusions) {
        public boolean matches(QuestLocation.Snapshot place) {
            if (!exclusions.allows(place, poiRadius)) return false;
            return switch (type) {
                case BIOME -> targets.contains(place.biome());
                case STRUCTURE -> !java.util.Collections.disjoint(targets, place.structures());
                case POI -> targets.stream().anyMatch(id -> place.hasPoi(id, poiRadius));
            };
        }
    }
    public record Lines(List<String> activity, List<String> news) {
        public static final Lines EMPTY = new Lines(List.of(), List.of());
    }
    public record Dialogues(Lines defaults, Map<String, Lines> loadouts) {
        /** Missing topics inherit independently, for the future ADM menu. */
        public Lines forLoadout(String id) {
            Lines override = loadouts.getOrDefault(id, Lines.EMPTY);
            return new Lines(override.activity().isEmpty() ? defaults.activity() : override.activity(),
                override.news().isEmpty() ? defaults.news() : override.news());
        }
    }
    public boolean eligible(String loadout, QuestLocation.Snapshot place) {
        return enabled && place.complete() && allowsLoadout(loadout)
            && (dimensions.isEmpty() || dimensions.contains(place.dimension()))
            && acceptance.allows(place, objective.poiRadius())
            && (!requireOutsideObjective || !objective.matches(place));
    }
    public boolean allowsLoadout(String loadout) {
        return (loadouts.isEmpty() || loadouts.contains(loadout)) && !excludedLoadouts.contains(loadout);
    }
    public boolean completedAt(NpcQuestState.Active active, QuestLocation.Snapshot place) {
        if (!enabled || !id.equals(active.id()) || !place.complete() || !active.dimension().equals(place.dimension())
            || (!dimensions.isEmpty() && !dimensions.contains(place.dimension()))) return false;
        double dx = (double)place.position().getX() - active.origin().getX();
        double dz = (double)place.position().getZ() - active.origin().getZ();
        return dx * dx + dz * dz >= objective.minTravelDistance() * objective.minTravelDistance()
            && objective.matches(place);
    }
}
