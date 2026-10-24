package mod.cdv.tnpc.inventory;

import java.util.*;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Cargo grows as needed. Equipped items remain in vanilla slots: no duplicate ownership. */
public final class NpcInventory {
    private final AutoGunOperator owner;
    private final List<ItemStack> cargo = new ArrayList<>();
    public NpcInventory(AutoGunOperator owner) { this.owner = owner; }
    public List<ItemStack> cargoSnapshot() { return cargo.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList(); }
    public List<ItemStack> allItems() {
        List<ItemStack> all = new ArrayList<>(cargoSnapshot());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = owner.getItemBySlot(slot);
            if (!stack.isEmpty()) all.add(stack.copy());
        }
        return all;
    }
    /** Copies the incoming stack; the caller must remove it from its previous owner. */
    public void store(ItemStack stack) { InventoryExchange.insert(cargo, stack); }
    public void replaceCargo(List<ItemStack> stacks) {
        cargo.clear(); for (ItemStack stack : stacks) if (!stack.isEmpty()) cargo.add(stack.copy());
    }
    public List<ItemStack> drainCargo() {
        List<ItemStack> result = cargoSnapshot(); cargo.clear(); return result;
    }
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack stack : cargo) if (!stack.isEmpty()) list.add(stack.save(registries));
        tag.put("tnpcInventory", list);
    }
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        cargo.clear();
        for (Tag entry : tag.getList("tnpcInventory", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.parseOptional(registries, (CompoundTag)entry);
            if (!stack.isEmpty()) cargo.add(stack);
        }
    }
}
