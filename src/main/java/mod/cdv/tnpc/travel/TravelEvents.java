package mod.cdv.tnpc.travel;

import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class TravelEvents {
    private boolean stopping;
    @SubscribeEvent public void starting(ServerStartingEvent event) { stopping = false; NpcTravel.reset(); }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) { stopping = true; }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { NpcTravel.reset(); }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        if (!stopping) NpcTravel.tick(event.getServer());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void join(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof AutoGunOperator npc
            && !NpcTravel.mayJoin(npc, level)) event.setCanceled(true);
    }
    @SubscribeEvent public void leave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof AutoGunOperator npc)) return;
        if (!stopping && npc.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            // Commit a travel intention while the complete entity data is still available.
            if (mod.cdv.tnpc.offline.OfflineEvents.claimAttempt(level.getServer())) NpcTravel.depart(npc, level, true);
        } else NpcTravel.removed(npc, level);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof AutoGunOperator npc && npc.level() instanceof ServerLevel level)
            NpcTravel.died(npc, level);
    }
}
