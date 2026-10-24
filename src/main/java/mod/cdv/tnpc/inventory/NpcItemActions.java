package mod.cdv.tnpc.inventory;

import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.world.entity.item.ItemEntity;

public final class NpcItemActions {
    private NpcItemActions() {}
    /** Explicit action only. No goal calls this for arbitrary ground loot. */
    public static boolean collect(AutoGunOperator npc, ItemEntity item) {
        if (npc.level().isClientSide || !npc.isAlive() || !item.isAlive() || item.hasPickUpDelay()
            || npc.level() != item.level() || npc.distanceToSqr(item) > 4 || !npc.getSensing().hasLineOfSight(item)) return false;
        var stack = item.getItem(); if (stack.isEmpty()) return false;
        npc.getNpcInventory().store(stack);
        npc.take(item, stack.getCount()); item.discard(); return true;
    }
    public static boolean share(AutoGunOperator from, AutoGunOperator to) {
        if (!canShare(from, to)) return false;
        var settings = NpcInventoryConfig.forLoadout(from.getTemplateId());
        long now = from.level().getGameTime();
        if (now < from.getPersistentData().getLong("tnpcNextItemShare")
            || now < to.getPersistentData().getLong("tnpcNextItemShare")) return false;
        from.getPersistentData().putLong("tnpcNextItemShare", now + settings.shareCooldownTicks());
        if (from.getRandom().nextDouble() >= settings.shareChance()) return false;
        return transfer(from, to);
    }
    /** Offline selection and its wallet replace the live conversation chance/cooldown. */
    public static boolean shareOffline(AutoGunOperator from, AutoGunOperator to) {
        return canShare(from, to) && transfer(from, to);
    }
    private static boolean canShare(AutoGunOperator from, AutoGunOperator to) {
        return !from.level().isClientSide && from.level() == to.level() && from != to && from.isAlive() && to.isAlive()
            && from.getTarget() == null && to.getTarget() == null && !from.isOnAlert() && !to.isOnAlert()
            && !from.isOnFire() && !to.isOnFire()
            && from.isAlliedTo(to) && to.isAlliedTo(from) && from.distanceToSqr(to) <= 36
            && from.getSensing().hasLineOfSight(to)
            && NpcInventoryConfig.forLoadout(from.getTemplateId()).shareItems()
            && NpcInventoryConfig.forLoadout(to.getTemplateId()).shareItems();
    }
    private static boolean transfer(AutoGunOperator from, AutoGunOperator to) {
        var sender = InventoryExchange.copy(from.getNpcInventory().cargoSnapshot());
        var receiver = to.getNpcInventory().cargoSnapshot();
        long countA = sender.stream().mapToLong(s -> s.getCount()).sum();
        long countB = receiver.stream().mapToLong(s -> s.getCount()).sum();
        if (countA <= countB + 1 || sender.isEmpty()) return false;
        var stack = sender.get(from.getRandom().nextInt(sender.size()));
        to.getNpcInventory().store(stack.split(1)); from.getNpcInventory().replaceCargo(sender);
        long now = from.level().getGameTime();
        from.getPersistentData().putLong("tnpcNextItemShare", now + NpcInventoryConfig.forLoadout(from.getTemplateId()).shareCooldownTicks());
        to.getPersistentData().putLong("tnpcNextItemShare", now + NpcInventoryConfig.forLoadout(to.getTemplateId()).shareCooldownTicks());
        return true;
    }
}
