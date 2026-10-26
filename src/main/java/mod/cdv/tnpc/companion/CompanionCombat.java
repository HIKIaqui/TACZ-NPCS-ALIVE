package mod.cdv.tnpc.companion;
import java.util.*;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.dialogue.NpcConversations;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Exact attacker UUIDs, not hostility categories. Damage memory expires after sixty seconds. */
@EventBusSubscriber(modid="tacznpcs")
public final class CompanionCombat {
    private static final Map<UUID,Map<UUID,Long>> attackers=new HashMap<>();
    private record Support(UUID owner,UUID target,long until) {}
    private static final Map<UUID,Support> support=new HashMap<>();
    public static void clearSupport(AutoGunOperator npc) { support.remove(npc.getUUID()); }
    @SubscribeEvent public static void hurt(LivingDamageEvent.Post event) {
        if(event.getNewDamage()<=0)return;
        if(event.getEntity() instanceof ServerPlayer player
            && event.getSource().getEntity() instanceof LivingEntity attacker && attacker!=player) {
            var memory=attackers.computeIfAbsent(player.getUUID(),id->new LinkedHashMap<>());
            long now=player.level().getGameTime();memory.entrySet().removeIf(e->e.getValue()<=now);
            if(memory.size()>=128)memory.remove(memory.keySet().iterator().next());
            memory.put(attacker.getUUID(),now+1200);
        }
        if(event.getSource().getEntity() instanceof ServerPlayer player && event.getEntity()!=player)
            supportAttack(player,event.getEntity());
    }
    private static void supportAttack(ServerPlayer player,LivingEntity target) {
        if(!target.isAlive() || target.isRemoved() || target.level()!=player.level())return;
        if(target instanceof AutoGunOperator friend && Companions.owns(friend,player))return;
        long now=player.level().getGameTime();support.entrySet().removeIf(e->e.getValue().until()<=now);
        for(var npc:player.level().getEntitiesOfClass(AutoGunOperator.class,player.getBoundingBox().inflate(64),
            npc->Companions.owns(npc,player) && defensive(npc) && npc.getTarget()==null)) {
            if(npc==target || !npc.isAlive() || npc.isAlliedTo(target) || !npc.canAttack(target)
                || NpcConversations.holdingCompanionCombat(npc) || catchingUp(npc) || farFromLeader(npc)
                || npc.distanceToSqr(target)>npc.getDetectionRange()*npc.getDetectionRange()
                || !npc.getSensing().hasLineOfSight(target))continue;
            support.put(npc.getUUID(),new Support(player.getUUID(),target.getUUID(),now+1200));
            npc.setTarget(target);
            if(npc.getTarget()!=target)support.remove(npc.getUUID());
        }
    }
    public static boolean defensive(AutoGunOperator npc) {
        var t=npc.getPersistentData().getCompound("tnpcCompanion");return Companions.active(npc) && (!t.contains("defensive") || t.getBoolean("defensive"));
    }
    public static boolean catchingUp(AutoGunOperator npc) { return Companions.active(npc) && !Companions.waiting(npc) && npc.getPersistentData().getCompound("tnpcCompanion").getBoolean("catchingUp"); }
    public static boolean farFromLeader(AutoGunOperator npc) {
        var p=Companions.leader(npc);return !Companions.waiting(npc) && p!=null && p.isAlive() && p.level()==npc.level() && npc.distanceToSqr(p)>Companions.followStartSqr(npc);
    }
    public static void update(AutoGunOperator npc) {
        if(!Companions.active(npc))return;
        var p=Companions.leader(npc);var t=npc.getPersistentData().getCompound("tnpcCompanion");
        boolean catching=t.getBoolean("catchingUp");
        if(Companions.waiting(npc) || p==null || !p.isAlive() || p.level()!=npc.level())catching=false;
        else if(npc.distanceToSqr(p)>Companions.followStartSqr(npc))catching=true;
        else if(npc.distanceToSqr(p)<=Companions.followStopSqr(npc))catching=false;
        t.putBoolean("catchingUp",catching);
        var target=npc.getTarget();
        if(target!=null && (!target.isAlive() || target.isRemoved() || target.level()!=npc.level() || !permits(npc,target)))npc.setTarget(null);
    }
    public static boolean permits(AutoGunOperator npc,LivingEntity target) {
        if(npc.level().isClientSide)return true; // Damage memory and combat authority live on the server.
        if(!Companions.active(npc))return true;
        if(!target.isAlive() || target.isRemoved() || target.level()!=npc.level())return false;
        if(NpcConversations.holdingCompanionCombat(npc) || catchingUp(npc) || farFromLeader(npc))return false;
        if(!defensive(npc))return true;
        if(npc.isAlliedTo(target))return false;
        var memory=attackers.get(Companions.owner(npc));
        long now=npc.level().getGameTime();
        var selected=support.get(npc.getUUID());
        return (memory!=null && memory.getOrDefault(target.getUUID(),Long.MIN_VALUE)>now)
            || (selected!=null && selected.owner().equals(Companions.owner(npc))
                && selected.target().equals(target.getUUID()) && selected.until()>now);
    }
    public static boolean acquires(AutoGunOperator npc,LivingEntity target) {
        return permits(npc,target) && (defensive(npc) || npc.isOpposedTo(target));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){var owner=event.getEntity().getUUID();attackers.remove(owner);support.entrySet().removeIf(e->e.getValue().owner().equals(owner));}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){attackers.clear();support.clear();}
}
