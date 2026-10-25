package mod.cdv.tnpc.inventory;

import java.util.*;
import java.util.function.*;
import net.minecraft.world.item.ItemStack;

/** Copy-only plan. One comparison per incoming stack-sized portion, never a forced reroll. */
public final class CargoPlanner {
    public record Plan(List<ItemStack> cargo,List<ItemStack> discarded,int accepted) {}
    private CargoPlanner() {}
    public static double replacementChance(double incoming,double existing) {
        return incoming>existing && incoming>0 ? Math.clamp(1-existing/incoming,0,1) : 0;
    }
    public static Plan plan(List<ItemStack> source,ItemStack incoming,int slots,ToDoubleFunction<ItemStack> interest,
                            Predicate<ItemStack> protectedItem,boolean replace,DoubleSupplier random) {
        var cargo=InventoryExchange.copy(source); cargo.removeIf(ItemStack::isEmpty);
        var remaining=incoming.copy(); var discarded=new ArrayList<ItemStack>();
        for(var stack:cargo) if(!remaining.isEmpty() && ItemStack.isSameItemSameComponents(stack,remaining)) {
            int n=Math.min(remaining.getCount(),Math.max(0,stack.getMaxStackSize()-stack.getCount())); stack.grow(n); remaining.shrink(n);
        }
        while(!remaining.isEmpty()) {
            int count=Math.min(remaining.getCount(),remaining.getMaxStackSize());
            if(cargo.size()<slots) { cargo.add(remaining.split(count)); continue; }
            if(!replace) break;
            double candidate=interest.applyAsDouble(remaining)*count;
            int victim=-1; double cost=Double.POSITIVE_INFINITY;
            for(int i=0;i<cargo.size();i++) {
                var old=cargo.get(i);
                if(protectedItem.test(old) || ItemStack.isSameItemSameComponents(old,remaining)) continue;
                double value=interest.applyAsDouble(old)*old.getCount();
                if(value<cost) { victim=i; cost=value; }
            }
            double chance=replacementChance(candidate,cost);
            if(victim<0 || chance<=0 || random.getAsDouble()>=chance) break;
            discarded.add(cargo.get(victim).copy()); cargo.set(victim,remaining.split(count));
        }
        return new Plan(List.copyOf(cargo),List.copyOf(discarded),incoming.getCount()-remaining.getCount());
    }
}
