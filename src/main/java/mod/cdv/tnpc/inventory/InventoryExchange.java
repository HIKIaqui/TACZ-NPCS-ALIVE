package mod.cdv.tnpc.inventory;

import java.util.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Pure, copy-based transaction planning. Callers commit only when both sides can pay. */
public final class InventoryExchange {
    private InventoryExchange() {}
    public record Plan(List<ItemStack> player, List<ItemStack> npc) {}
    public static void insert(List<ItemStack> stacks, ItemStack input) {
        ItemStack remaining = input.copy();
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameComponents(stack, remaining)) {
                int amount = Math.min(remaining.getCount(), Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                stack.grow(amount); remaining.shrink(amount);
                if (remaining.isEmpty()) return;
            }
        }
        while (!remaining.isEmpty()) stacks.add(remaining.split(Math.min(remaining.getCount(), remaining.getMaxStackSize())));
    }
    public static List<ItemStack> copy(List<ItemStack> input) {
        return input.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    private static List<ItemStack> take(List<ItemStack> source, Map<Item, Integer> requirements) {
        return take(source,requirements,s -> true);
    }
    private static List<ItemStack> take(List<ItemStack> source, Map<Item, Integer> requirements, java.util.function.Predicate<ItemStack> allowed) {
        List<ItemStack> taken = new ArrayList<>();
        for (var entry : requirements.entrySet()) {
            int remaining = entry.getValue();
            if (remaining <= 0) return null;
            for (ItemStack stack : source) if (stack.is(entry.getKey()) && allowed.test(stack)) {
                int count = Math.min(remaining, stack.getCount());
                if (count > 0) taken.add(stack.split(count));
                remaining -= count; if (remaining == 0) break;
            }
            if (remaining != 0) return null;
        }
        return taken;
    }
    private static boolean insertPlayer(List<ItemStack> slots, ItemStack incoming) {
        ItemStack remaining = incoming.copy();
        for (ItemStack stack : slots) if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, remaining)) {
            int count = Math.min(remaining.getCount(), Math.max(0, stack.getMaxStackSize() - stack.getCount()));
            stack.grow(count); remaining.shrink(count); if (remaining.isEmpty()) return true;
        }
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).isEmpty()) {
            slots.set(i, remaining.split(Math.min(remaining.getCount(), remaining.getMaxStackSize())));
            if (remaining.isEmpty()) return true;
        }
        return remaining.isEmpty();
    }
    public static Plan plan(List<ItemStack> player, List<ItemStack> npc,
                            Map<Item, Integer> cost, Map<Item, Integer> result) {
        return plan(player,npc,cost,result,Integer.MAX_VALUE,s -> true);
    }
    public static Plan plan(List<ItemStack> player,List<ItemStack> npc,Map<Item,Integer> cost,Map<Item,Integer> result,
                            int maxNpcSlots,java.util.function.Predicate<ItemStack> canGive) {
        if (cost.isEmpty() || result.isEmpty()) return null;
        List<ItemStack> p = copy(player), n = copy(npc);
        List<ItemStack> payment = take(p, cost), delivery = take(n, result, canGive);
        if (payment == null || delivery == null) return null;
        for (ItemStack stack : delivery) if (!insertPlayer(p, stack)) return null;
        n.removeIf(ItemStack::isEmpty);
        for (ItemStack stack : payment) insert(n, stack);
        if(n.size()>maxNpcSlots) return null;
        return new Plan(p, n);
    }
    public static boolean has(List<ItemStack> stacks,Map<Item,Integer> required,java.util.function.Predicate<ItemStack> allowed) {
        return take(copy(stacks),required,allowed)!=null;
    }
    public static boolean has(List<ItemStack> stacks, Map<Item, Integer> required) {
        return take(copy(stacks), required) != null;
    }
}
