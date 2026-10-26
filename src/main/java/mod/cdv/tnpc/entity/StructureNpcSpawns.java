package mod.cdv.tnpc.entity;

import mod.cdv.tnpc.TNPC;
import mod.cdv.tnpc.data.StructureTemplate;
import mod.cdv.tnpc.data.TemplateDataManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Defers structure replacements until the entity actually ticks on the server. */
@EventBusSubscriber(modid = TNPC.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class StructureNpcSpawns {
    private static final String PENDING = "tacznpcs:pending_structure_replacement";

    private StructureNpcSpawns() {}

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.isSpawnCancelled() || event.getEntity() instanceof AutoGunOperator) return;
        if (event.getSpawnType() != MobSpawnType.STRUCTURE
                && event.getSpawnType() != MobSpawnType.NATURAL) return;

        // This callback may run on a C2ME/DH generation worker. Touch only the
        // newly spawned entity, never ServerLevel, shared config, loot or RNG.
        // ForgeData persists the marker even if pre-generation saves this mob
        // before it gets its first tick. No global queue or chunk tickets needed.
        event.getEntity().getPersistentData().putBoolean(PENDING, true);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob original)
                || !(original.level() instanceof ServerLevel level)
                || !original.getPersistentData().getBoolean(PENDING)) return;
        // Also defend against third-party mods moving entity ticks off-thread.
        if (!level.getServer().isSameThread()) return;

        // Consume before attempting replacement: a failed chance is final, and
        // subsequent ticks/reloads must not repeatedly roll for the same mob.
        original.getPersistentData().remove(PENDING);
        if (original.isRemoved() || !original.isAlive()) return;
        if (replace(original, level)) event.setCanceled(true);
    }

    private static boolean replace(Mob original, ServerLevel level) {
        if (TemplateDataManager.STRUCTURES.isEmpty()) return false;
        String originalId = EntityType.getKey(original.getType()).toString();
        for (Structure structure : level.structureManager()
                .getAllStructuresAt(original.blockPosition()).keySet()) {
            ResourceLocation id = level.registryAccess()
                    .registryOrThrow(Registries.STRUCTURE).getKey(structure);
            if (id == null) continue;
            var templates = TemplateDataManager.STRUCTURES.get(id.toString());
            if (templates == null) continue;
            for (StructureTemplate template : templates) {
                if (!template.toReplace().contains(originalId)
                        || !(level.random.nextFloat() < template.chance())) continue;
                AutoGunOperator replacement = AutoGunOperator.create(level, template.loadout());
                replacement.moveTo(original.position());
                replacement.setXRot(original.getXRot());
                replacement.setYRot(original.getYRot());
                // Retain the original if another mod vetoes the replacement.
                // Stop after one match, including overlapping structure rules.
                if (!level.addFreshEntity(replacement)) return false;
                original.discard();
                return true;
            }
        }
        return false;
    }
}
