package mod.cdv.tnpc.reputation;

import java.util.*;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.inventory.NpcInventoryTrades;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Death attribution is captured once; apply after all death listeners can veto the event. */
@EventBusSubscriber(modid="tacznpcs")
public final class NpcKillReputation {
    private static final String APPLIED="tnpcKillReputationApplied";
    private record Pending(AutoGunOperator npc,ServerPlayer player,String faction,LivingDeathEvent event) {}
    private static final Map<UUID,Pending> pending=new LinkedHashMap<>();
    @SubscribeEvent
    public static void damaged(LivingDamageEvent.Post event) {
        if(event.getNewDamage()<=0 || !(event.getEntity() instanceof AutoGunOperator npc)
            || npc.level().isClientSide || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        boolean tolerated=ReputationConfig.settings().allied(ReputationAccess.effective(npc,player));
        var data=ReputationData.get(player.server);int before=data.personal(player.getUUID(),npc.getUUID());
        int after=ReputationData.add(before,-5);data.personal(player.getUUID(),npc.getUUID(),after);
        if(tolerated) { if(npc.getTarget()==player) npc.setTarget(null); }
        else if(npc.isAlive()) npc.setTarget(player);
        if(after<before) NpcInventoryTrades.message(player,"Personal reputation ("+npc.getDisplayName().getString()+") "+(after-before));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if(!(event.getEntity() instanceof AutoGunOperator npc) || npc.level().isClientSide) return;
        var source=event.getSource().getEntity();
        // Projectiles, including TaCZ bullets, expose their shooter as the causing entity.
        // Environmental finishes may retain vanilla's player kill credit.
        var killer=source instanceof ServerPlayer player ? player : source==null && npc.getKillCredit() instanceof ServerPlayer player ? player : null;
        if(killer!=null) pending.put(npc.getUUID(),new Pending(npc,killer,npc.getFactionId(),event));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { flush(); }
    public static void flush() {
        var deaths=new ArrayList<>(pending.values());pending.clear();
        for(var death:deaths) {
            var npc=death.npc();
            if(death.event().isCanceled() || !npc.isDeadOrDying() || npc.getPersistentData().getBoolean(APPLIED)) continue;
            npc.getPersistentData().putBoolean(APPLIED,true);
            var data=ReputationData.get(death.player().server);
            int before=data.faction(death.player().getUUID(),death.faction());
            int after=ReputationData.add(before,-5);
            data.faction(death.player().getUUID(),death.faction(),after);
            if(after<before) NpcInventoryTrades.message(death.player(),"Faction reputation ("+death.faction()+") "+(after-before));
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { pending.clear(); }
}
