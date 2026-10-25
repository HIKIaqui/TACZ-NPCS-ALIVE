package mod.cdv.tnpc.inventory;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.util.*;
import mod.cdv.tnpc.dialogue.NpcConversations;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.network.ConversationClosePacket;
import net.aviel.dialogue.network.OpenNpcTradePacket;
import net.aviel.dialogue.npc.NpcTradeService;
import net.aviel.dialogue.npc.trade.NpcTradeDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Replaces ADM's infinite-shop transaction only for our NPCs. ADM still renders its own UI. */
public final class NpcInventoryTrades {
    private record Shop(AutoGunOperator npc, String requested, String file, boolean opened, long lastTradeTick, NpcTradeDefinition generated) {}
    private static final Map<UUID, Shop> shops = new HashMap<>();
    public static void authorize(AutoGunOperator npc, ServerPlayer player, String requested) {
        shops.put(player.getUUID(), new Shop(npc, requested, "", false, Long.MIN_VALUE, null));
    }
    public static boolean active(AutoGunOperator npc, ServerPlayer player) {
        Shop shop = shops.get(player.getUUID()); return shop != null && shop.npc() == npc && shop.opened();
    }
    public static boolean closingTrade(AutoGunOperator npc, ServerPlayer player, String file) {
        Shop shop = shops.get(player.getUUID()); return shop != null && shop.npc() == npc && shop.opened() && shop.file().equals(file);
    }
    public static void close(AutoGunOperator npc, ServerPlayer player) {
        Shop shop = shops.get(player.getUUID());
        if (shop != null && shop.npc() == npc) {
            shops.remove(player.getUUID());
            if (shop.opened()) PacketDistributor.sendToPlayer(player, new ConversationClosePacket(npc.getUUID(), shop.file()));
        }
    }
    public static void clear() { shops.clear(); }
    public static String decorate(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject nodes = root.getAsJsonObject("nodes");
        String start = root.has("start") ? root.get("start").getAsString() : nodes.keySet().iterator().next();
        if (!nodes.has(start)) return json;
        JsonObject node = nodes.getAsJsonObject(start);
        JsonArray old = node.has("choices") ? node.getAsJsonArray("choices") : new JsonArray();
        JsonArray choices = new JsonArray();
        choices.add(JsonParser.parseString("""
            {"id":"tnpc_inventory_trade","text":"Let's trade.","trade":"tnpc_inventory.json",
             "condition":{"type":"tnpc.can_trade"}}
            """));
        for (var choice : old) if (!choice.isJsonObject() || !choice.getAsJsonObject().has("id") || !choice.getAsJsonObject().get("id").getAsString().equals("tnpc_inventory_gift")) choices.add(choice); node.add("choices", choices); return root.toString();
    }
    public static void open(ServerPlayer player, AutoGunOperator npc, String requested) {
        Shop authorized = shops.get(player.getUUID());
        if (authorized == null || authorized.npc() != npc || !authorized.requested().equals(requested)
            || !NpcConversations.canTrade(npc, player) || !NpcInventoryConfig.forLoadout(npc.getTemplateId()).canTrade()) return;
        try {
            String reference = requested.equals("tnpc_inventory.json")
                ? NpcInventoryConfig.tradeFile(npc.getTemplateId(), java.util.Set.of(npc.getFactionId())) : requested;
            boolean generated = reference.isBlank();
            String file = generated ? "tnpc_generated.json" : NpcTradeService.resolveTradeFileName(player.server, reference);
            if (file == null) throw new IllegalArgumentException("Trade file not found: " + reference);
            String json = generated ? NpcGenericTrades.create(NpcInventoryConfig.forLoadout(npc.getTemplateId()),
                NpcInventoryConfig.generic(), npc.getNpcInventory().cargoSnapshot()) : NpcTradeService.readTradeJson(player.server, file);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (generated && root.getAsJsonArray("offers").isEmpty()) {
                shops.remove(player.getUUID()); message(player, "This NPC has no trade offers right now."); return;
            }
            var definition = NpcTradeDefinition.fromJson(json);
            for (String key : List.of("offers", "trades")) if (root.has(key)) {
                int index = 0;
                for (var entry : root.getAsJsonArray(key)) {
                    if (entry.isJsonObject()) {
                        var object = entry.getAsJsonObject();
                        String id = object.has("id") ? object.get("id").getAsString() : "offer_" + index;
                        var offer = definition.offerByIdOrIndex(id, index);
                        boolean locked = offer == null || !NpcTradeService.isOfferAvailable(player, offer)
                            || !InventoryExchange.has(npc.getNpcInventory().cargoSnapshot(), totals(offer.result(), 1), s -> !NpcInventoryConfig.forLoadout(npc.getTemplateId()).protectedItem(s));
                        object.addProperty("locked", locked);
                    }
                    index++;
                }
            }
            if (root.toString().length() > 30000) throw new IllegalArgumentException("Trade file exceeds ADM packet limit");
            shops.put(player.getUUID(), new Shop(npc, requested, file, true, Long.MIN_VALUE, generated ? definition : null));
            PacketDistributor.sendToPlayer(player, new OpenNpcTradePacket(npc.getUUID(), npc.getDisplayName().getString(), file, root.toString()));
        } catch (Exception e) {
            shops.remove(player.getUUID()); message(player, "Could not open this NPC's trade. Check the server log.");
            LogUtils.getLogger().error("NPC inventory shop failed", e);
        }
    }
    public static boolean execute(ServerPlayer player, AutoGunOperator npc, String file, String id, int index, int amount) {
        Shop shop = shops.get(player.getUUID());
        long now = npc.level().getGameTime();
        if (shop == null || shop.npc() != npc || !shop.opened() || !shop.file().equals(file)
            || amount < 1 || amount > 64 || shop.lastTradeTick() == now || !NpcConversations.canTrade(npc, player)
            || !NpcInventoryConfig.forLoadout(npc.getTemplateId()).canTrade()) {
            message(player, "This trade is no longer available."); return false;
        }
        try {
            var definition = shop.generated() != null ? shop.generated() : NpcTradeService.loadTrade(player.server, file);
            var offer = definition.offerByIdOrIndex(id, index);
            if (offer == null || !NpcTradeService.isOfferAvailable(player, offer)) return false;
            // Commands are intentionally not a second source of items/side effects in a finite-stock shop.
            if (!offer.commands().isEmpty()) {
                message(player, "NPC inventory trades cannot run offer commands."); return false;
            }
            List<ItemStack> playerItems = new ArrayList<>();
            for (int i = 0; i < 36; i++) playerItems.add(player.getInventory().getItem(i).copy());
            var plan = InventoryExchange.plan(playerItems, npc.getNpcInventory().cargoSnapshot(), totals(offer.cost(), amount), totals(offer.result(), amount),
                Math.max(NpcInventoryConfig.forLoadout(npc.getTemplateId()).maxCargoSlots(),npc.getNpcInventory().cargoSnapshot().size()),
                s -> !NpcInventoryConfig.forLoadout(npc.getTemplateId()).protectedItem(s));
            if (plan == null || !npc.getNpcInventory().canReplaceCargo(plan.npc())) {
                message(player, "Not enough items, NPC stock, or inventory space."); return false;
            }
            npc.getNpcInventory().replaceCargo(plan.npc());
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, plan.player().get(i));
            player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
            shops.put(player.getUUID(), new Shop(npc, shop.requested(), shop.file(), true, now, shop.generated()));
            message(player, "Trade complete."); return true;
        } catch (Exception e) {
            LogUtils.getLogger().error("NPC inventory trade failed", e); message(player, "Trade failed; check the server log."); return false;
        }
    }
    private static Map<Item, Integer> totals(Iterable<NpcTradeDefinition.TradeItem> rules, int amount) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        for (var rule : rules) {
            var key = ResourceLocation.tryParse(rule.item());
            Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
            if (item == Items.AIR) throw new IllegalArgumentException("Invalid trade item");
            int count = Math.multiplyExact(rule.count(), amount);
            result.merge(item, count, Math::addExact);
        }
        return result;
    }
    public static void message(ServerPlayer player, String text) { player.displayClientMessage(Component.literal(text), true); }
}
