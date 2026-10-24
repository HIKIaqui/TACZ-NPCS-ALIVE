// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.data;

import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentTable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import org.jetbrains.annotations.Nullable;

public record NPCTemplate(Set<String> aligned, Set<String> opposed, List<String> names, @Nullable EquipmentTable equipment, Either<LootTable, ItemStack[]> helmet, Either<LootTable, ItemStack[]> chestPlate, Either<LootTable, ItemStack[]> leggings, Either<LootTable, ItemStack[]> boots, Either<LootTable, ItemStack[]> primary, Either<LootTable, ItemStack[]> secondary, List<SkinTemplate> texture) {
    private static final Set<String> EMPTY = Set.of();
    private static final Either<LootTable, ItemStack[]> EMPTY_ITEM = Either.right(new ItemStack[0]);
    public static final SkinTemplate DEFAULT_SKIN = new SkinTemplate("textures/entity/player/wide/steve.png", false);
    public static final NPCTemplate DEFAULT = new NPCTemplate(EMPTY, EMPTY, List.of(), null, EMPTY_ITEM, EMPTY_ITEM, EMPTY_ITEM, EMPTY_ITEM, EMPTY_ITEM, EMPTY_ITEM, List.of(DEFAULT_SKIN));

    public List<ItemStack> randomEquip(ServerLevel level) {
        ArrayList<ItemStack> items = new ArrayList<ItemStack>(6);
        NPCTemplate.addRandom(items, this.helmet, level);
        NPCTemplate.addRandom(items, this.chestPlate, level);
        NPCTemplate.addRandom(items, this.leggings, level);
        NPCTemplate.addRandom(items, this.boots, level);
        NPCTemplate.addRandom(items, this.primary, level);
        NPCTemplate.addRandom(items, this.secondary, level);
        return items;
    }

    private static void addRandom(List<ItemStack> items, Either<LootTable, ItemStack[]> either, ServerLevel level) {
        either.ifLeft(table -> {
            ItemStack stack = NPCTemplate.firstFromTable(table, level);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        });
        either.ifRight(arr -> {
            if (((ItemStack[])arr).length > 0) {
                items.add(arr[level.random.nextInt(((ItemStack[])arr).length)].copy());
            }
        });
    }

    private static ItemStack firstFromTable(LootTable table, ServerLevel level) {
        ObjectArrayList<ItemStack> generated = table.getRandomItems(new LootParams.Builder(level).create(LootContextParamSet.builder().build()));
        return generated.isEmpty() ? ItemStack.EMPTY : (ItemStack)generated.get(0);
    }

    public record SkinTemplate(String texture, boolean slim) {
    }
}

