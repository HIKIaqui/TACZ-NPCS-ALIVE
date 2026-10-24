package mod.cdv.tnpc.quest;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;

/** Bounded queries against already loaded terrain. No locate or chunk-generation calls. */
public final class QuestLocation {
    public record Snapshot(String dimension, BlockPos position, String biome, Set<String> structures,
                           Map<String, Double> poiDistancesSquared, boolean complete) {
        public boolean hasPoi(String type, int radius) {
            return poiDistancesSquared.getOrDefault(type, Double.POSITIVE_INFINITY) <= (double)radius * radius;
        }
    }
    private QuestLocation() {}
    public static Snapshot inspect(ServerLevel level, BlockPos pos, QuestConfig.Settings settings) {
        return inspect(level, pos, settings.quests().values());
    }
    public static Snapshot inspect(ServerLevel level, BlockPos pos, Collection<QuestDefinition> definitions) {
        Set<String> structures = new HashSet<>(); Map<String, Double> pois = new HashMap<>();
        boolean[] complete = {true};
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) throw new IllegalStateException("Quest inspection requires a loaded NPC chunk");
        Set<String> relevant = new HashSet<>(); int radius = -1;
        for (var quest : definitions) if (quest.enabled()) {
            if (quest.objective().type() == QuestDefinition.Type.STRUCTURE) relevant.addAll(quest.objective().targets());
            relevant.addAll(quest.acceptance().excludedStructures());
            relevant.addAll(quest.objective().exclusions().excludedStructures());
            if (quest.objective().type() == QuestDefinition.Type.POI || !quest.acceptance().excludedPoiTypes().isEmpty()
                || !quest.objective().exclusions().excludedPoiTypes().isEmpty()) radius = Math.max(radius, quest.objective().poiRadius());
        }
        if (!relevant.isEmpty()) {
            var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            // References can point to unloaded start chunks: skip those instead of loading them.
            chunk.getAllReferences().forEach((structure, references) -> {
                var structureId = registry.getKey(structure);
                if (structureId == null || !relevant.contains(structureId.toString())) return;
                for (long reference : references) {
                    ChunkPos startPos = new ChunkPos(reference);
                    var startChunk = level.getChunkSource().getChunkNow(startPos.x, startPos.z);
                    if (startChunk == null) { complete[0] = false; continue; }
                    var start = startChunk.getStartForStructure(structure);
                    if (start != null && start.isValid() && start.getBoundingBox().isInside(pos)) {
                        var id = registry.getKey(structure);
                        if (id != null) structures.add(id.toString());
                        break;
                    }
                }
            });
            // Include starts in this chunk even if reference data has not been populated yet.
            chunk.getAllStarts().forEach((structure, start) -> {
                if (start != null && start.isValid() && start.getBoundingBox().isInside(pos)) {
                    var id = registry.getKey(structure); if (id != null) structures.add(id.toString());
                }
            });
        }
        if (radius >= 0) {
            final int queryRadius = radius;
            for (int x = (pos.getX() - radius) >> 4; x <= (pos.getX() + radius) >> 4; x++)
                for (int z = (pos.getZ() - radius) >> 4; z <= (pos.getZ() + radius) >> 4; z++) {
                    if (level.getChunkSource().getChunkNow(x, z) == null) { complete[0] = false; continue; }
                    try (var records = level.getPoiManager().getInChunk(holder -> true, new ChunkPos(x, z), PoiManager.Occupancy.ANY)) {
                        records.forEach(record -> {
                            double distance = record.getPos().distSqr(pos);
                            if (distance > (double)queryRadius * queryRadius) return;
                            record.getPoiType().unwrapKey().ifPresent(key -> pois.merge(key.location().toString(), distance, Math::min));
                        });
                    }
                }
        }
        String biome = level.getBiome(pos).unwrapKey().orElseThrow().location().toString();
        return new Snapshot(level.dimension().location().toString(), pos.immutable(), biome, Set.copyOf(structures), Map.copyOf(pois), complete[0]);
    }
}
