package mod.cdv.tnpc.entity;

import mod.cdv.tnpc.data.NpcBehaviorConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class DamageEscapeEvents {
    @SubscribeEvent
    public void damaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getEntity().level() instanceof ServerLevel)) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof AutoGunOperator npc) npc.reactToUnknownDanger(npc.position());
        alarmAllies(victim);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void died(LivingDeathEvent event) {
        alarmAllies(event.getEntity());
    }

    private void alarmAllies(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        // 48 is the validated upper bound for allyAlertRadius. No chunk is forced to load.
        for (AutoGunOperator npc : level.getEntitiesOfClass(AutoGunOperator.class, victim.getBoundingBox().inflate(48))) {
            var settings = NpcBehaviorConfig.damageEscapeFor(npc.getTemplateId());
            double radius = NpcBehaviorConfig.perceptionFor(npc.getTemplateId()).allyAlertRadius();
            if (npc == victim || !npc.isAlive() || npc.getTarget() != null || npc.isOnAlert()
                || npc.getDamageEscapeMemory().active(level.getGameTime())
                || !settings.enabled() || !settings.reactToAllies() || radius <= 0
                || npc.distanceToSqr(victim) > radius * radius
                || !npc.isAlliedTo(victim) || npc.isOpposedTo(victim)) continue;
            npc.reactToUnknownDanger(victim.position());
        }
    }
}
