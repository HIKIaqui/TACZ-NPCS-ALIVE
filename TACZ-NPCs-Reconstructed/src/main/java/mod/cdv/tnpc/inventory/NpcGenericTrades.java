package mod.cdv.tnpc.inventory;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import com.tacz.guns.item.ModernKineticGunItem;

/** Generates server-owned offer definitions; never generates stock or currency. */
public final class NpcGenericTrades {
    public static String create(NpcInventoryConfig.Profile profile, NpcInventoryConfig.Generic prices, List<ItemStack> cargo) {
        var currencyId = ResourceLocation.parse(prices.currency());
        Item currency = BuiltInRegistries.ITEM.get(currencyId);
        if (currency == Items.AIR) throw new IllegalArgumentException("Unknown generic trade currency: " + currencyId);
        // Desired goods can be bought even before the NPC owns any; carried spare equipment can also be sold.
        Set<Item> desired = new LinkedHashSet<>();
        for (Item item : BuiltInRegistries.ITEM) if (item != currency && item != Items.AIR && profile.interested(new ItemStack(item))) desired.add(item);
        Set<Item> goods = new LinkedHashSet<>(desired);
        for (ItemStack stack : cargo) if (!stack.isEmpty() && stack.getItem() != currency
            && (stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ModernKineticGunItem)) goods.add(stack.getItem());
        JsonObject root = new JsonObject(); root.addProperty("title", "Trade");
        root.addProperty("subtitle", "Real stock. Buy and sell prices are set by the server.");
        JsonArray offers = new JsonArray(); root.add("offers", offers);
        for (Item item : goods) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            int quantity = Math.min(prices.quantity(), item.getDefaultMaxStackSize());
            // Buy = NPC buys from player; sell = NPC sells to player.
            if (desired.contains(item)) offers.add(offer("buy_" + id, "Sell " + item.getDescription().getString(), id, quantity, prices.currency(), prices.buyPrice()));
            offers.add(offer("sell_" + id, "Buy " + item.getDescription().getString(), prices.currency(), prices.sellPrice(), id, quantity));
        }
        if (root.toString().length() > 28000) throw new IllegalArgumentException("Too many generic offers for ADM; narrow interests or configure a custom tradeFile");
        return root.toString();
    }
    private static JsonObject offer(String id, String title, String cost, int costCount, String result, int resultCount) {
        JsonObject o = new JsonObject(); o.addProperty("id", id); o.addProperty("title", title);
        o.add("cost", item(cost,costCount)); o.add("result", item(result,resultCount)); return o;
    }
    private static JsonObject item(String id, int count) {
        JsonObject o = new JsonObject(); o.addProperty("item",id); o.addProperty("count",count); return o;
    }
}
