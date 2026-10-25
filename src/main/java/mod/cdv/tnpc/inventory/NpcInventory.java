package mod.cdv.tnpc.inventory;

import java.util.*;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Bounded new cargo; existing/initial cargo is never truncated. Equipment has separate ownership. */
public final class NpcInventory {
    private final AutoGunOperator owner;
    private final List<ItemStack> cargo=new ArrayList<>();
    private final Map<UUID,Long> retry=new LinkedHashMap<>();
    public NpcInventory(AutoGunOperator owner) { this.owner=owner; }
    private NpcInventoryConfig.Profile settings() { return NpcInventoryConfig.forLoadout(owner.getTemplateId()); }
    public List<ItemStack> cargoSnapshot() { return cargo.stream().filter(s->!s.isEmpty()).map(ItemStack::copy).toList(); }
    public List<ItemStack> allItems() {
        List<ItemStack> all=new ArrayList<>(cargoSnapshot());
        for(EquipmentSlot slot:EquipmentSlot.values()) { var stack=owner.getItemBySlot(slot); if(!stack.isEmpty()) all.add(stack.copy()); }
        return all;
    }
    /** Template extras are created before final positioning. Retain them, including intentional oversized templates. */
    public void storeInitial(ItemStack stack) { InventoryExchange.insert(cargo,stack); }
    /** No replacement or dropping: returns the exact number accepted, leaving the input untouched. */
    public int insert(ItemStack stack) {
        var p=CargoPlanner.plan(cargo,stack,settings().maxCargoSlots(),s->0,s->true,false,()->0);
        replaceCargo(p.cargo()); return p.accepted();
    }
    /** Ground/offline loot uses interest priority. Dropped victims must spawn before the cargo plan commits. */
    public int collect(ItemStack stack) {
        var policy=settings(); if(!policy.interested(stack)) return 0;
        var p=CargoPlanner.plan(cargo,stack,policy.maxCargoSlots(),policy::interest,policy::protectedItem,true,owner.getRandom()::nextDouble);
        if(!publish(p.discarded())) return 0;
        replaceCargo(p.cargo()); return p.accepted();
    }
    public int collect(ItemEntity item) {
        long now=owner.level().getGameTime(); if(now<retry.getOrDefault(item.getUUID(),Long.MIN_VALUE)) return 0;
        int accepted=collect(item.getItem());
        if(accepted<item.getItem().getCount()) {
            if(retry.size()>=128) retry.remove(retry.keySet().iterator().next());
            retry.put(item.getUUID(),now+settings().pickupRetryTicks());
        } else retry.remove(item.getUUID());
        return accepted;
    }
    /** Equipment upgrades must preserve the old piece. Cancel the upgrade if its overflow drop is rejected. */
    public boolean stashEquipment(ItemStack stack) {
        if(!owner.isAddedToLevel()) { storeInitial(stack); return true; }
        var p=CargoPlanner.plan(cargo,stack,settings().maxCargoSlots(),s->0,s->true,false,()->0);
        var overflow=stack.copyWithCount(stack.getCount()-p.accepted());
        if(!overflow.isEmpty() && (settings().protectedItem(stack) || !publish(List.of(overflow)))) return false;
        replaceCargo(p.cargo()); return true;
    }
    private boolean publish(List<ItemStack> stacks) {
        if(stacks.isEmpty()) return true;
        if(owner.level().isClientSide || owner.isRemoved() || !owner.isAddedToLevel()) return false;
        List<ItemEntity> spawned=new ArrayList<>();
        try {
            for(var stack:stacks) {
                var item=new ItemEntity(owner.level(),owner.getX(),owner.getY()+.25,owner.getZ(),stack.copy()); item.setPickUpDelay(100);
                spawned.add(item);
                if(!owner.level().addFreshEntity(item)) { spawned.forEach(ItemEntity::discard); return false; }
            }
        } catch(RuntimeException failure) {
            spawned.forEach(ItemEntity::discard);
            com.mojang.logging.LogUtils.getLogger().error("NPC cargo drop rejected; preserving previous inventory",failure); return false;
        }
        return true;
    }
    public boolean canReplaceCargo(List<ItemStack> stacks) {
        long size=stacks.stream().filter(s->!s.isEmpty()).count();
        return size<=Math.max(settings().maxCargoSlots(),cargoSnapshot().size());
    }
    /** Commit/restore only: callers validate physical transfers before replacing cargo. */
    public void replaceCargo(List<ItemStack> stacks) { cargo.clear(); for(var stack:stacks) if(!stack.isEmpty()) cargo.add(stack.copy()); }
    public List<ItemStack> drainCargo() { var result=cargoSnapshot(); cargo.clear(); return result; }
    public void save(CompoundTag tag,HolderLookup.Provider registries) {
        var list=new ListTag(); for(var stack:cargo) if(!stack.isEmpty()) list.add(stack.save(registries)); tag.put("tnpcInventory",list);
    }
    public void load(CompoundTag tag,HolderLookup.Provider registries) {
        cargo.clear(); retry.clear();
        for(var entry:tag.getList("tnpcInventory",Tag.TAG_COMPOUND)) { var stack=ItemStack.parseOptional(registries,(CompoundTag)entry); if(!stack.isEmpty()) cargo.add(stack); }
    }
}
