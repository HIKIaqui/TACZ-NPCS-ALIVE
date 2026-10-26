package mod.cdv.tnpc.inventory;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.util.*;
import mod.cdv.tnpc.dialogue.NpcConversations;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.network.ConversationClosePacket;
import net.aviel.dialogue.network.OpenNpcTradePacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Replaces ADM's infinite-shop transaction only for our NPCs. ADM still renders its own UI. */
public final class NpcInventoryTrades {
    private record Shop(AutoGunOperator npc, String requested, String file, boolean opened, long lastTradeTick,
                        NpcGenericTrades.Shop quotes,Object policy) {}
    private static final Map<UUID, Shop> shops = new HashMap<>();
    public static void authorize(AutoGunOperator npc, ServerPlayer player, String requested) {
        shops.put(player.getUUID(), new Shop(npc, requested, "", false, Long.MIN_VALUE, null, NpcInventoryConfig.policyToken()));
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
            var profile=NpcInventoryConfig.forLoadout(npc.getTemplateId());
            var quotes=NpcGenericTrades.create(profile,NpcInventoryConfig.currencies(),npc.getNpcInventory().cargoSnapshot(),
                mod.cdv.tnpc.reputation.ReputationAccess.effective(npc,player));
            if(quotes.quotes().isEmpty()) {
                shops.remove(player.getUUID()); message(player,"This NPC has no trade offers right now."); return;
            }
            // A fresh token prevents an old GUI's queued packet selecting a different item after reopening.
            String file="tnpc_stock_"+UUID.randomUUID()+".json";
            var shop=new Shop(npc,requested,file,true,Long.MIN_VALUE,quotes,NpcInventoryConfig.policyToken());
            send(player,shop,false);
            shops.put(player.getUUID(),shop);
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
            if(shop.policy()!=NpcInventoryConfig.policyToken()) {
                message(player,"Trade settings changed. Reopen this shop."); return false;
            }
            // Never trust client-supplied item IDs, prices, indexes, or component data.
            var quotes=shop.quotes().quotes();
            if(index<0 || index>=quotes.size()) return false;
            var quote=quotes.get(index);
            if(!quote.id().equals(id)) return false;
            var profile=NpcInventoryConfig.forLoadout(npc.getTemplateId());
            Map<Item,Integer> cost=new LinkedHashMap<>();
            quote.payment().counts().forEach((key,count)->cost.put(BuiltInRegistries.ITEM.get(ResourceLocation.parse(key)),Math.multiplyExact(count,amount)));
            List<ItemStack> playerItems=new ArrayList<>();
            for(int i=0;i<net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE;i++) playerItems.add(player.getInventory().getItem(i).copy());
            var cargo=npc.getNpcInventory().cargoSnapshot();
            var plan=InventoryExchange.exact(playerItems,cargo,cost,quote.prototype(),amount,
                Math.max(profile.maxCargoSlots(),cargo.size()),s->!profile.protectedItem(s));
            if(plan==null || !npc.getNpcInventory().canReplaceCargo(plan.npc())) {
                message(player,"Not enough payment, exact NPC stock, or inventory space."); return false;
            }
            npc.getNpcInventory().replaceCargo(plan.npc());
            for(int i=0;i<playerItems.size();i++) player.getInventory().setItem(i,plan.player().get(i));
            player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
            var updated=new Shop(npc,shop.requested(),shop.file(),true,now,shop.quotes(),shop.policy());
            shops.put(player.getUUID(),updated);
            // A refresh error must never report a completed transaction as a failed purchase.
            try { send(player,updated,true); }
            catch(Exception refresh) { LogUtils.getLogger().error("Could not refresh NPC shop after completed purchase",refresh); }
            message(player,"Trade complete."); return true;
        } catch(Exception e) {
            LogUtils.getLogger().error("NPC inventory trade failed",e); message(player,"Trade failed; check the server log."); return false;
        }
    }
    private static void send(ServerPlayer player,Shop shop,boolean refresh) {
        String json=NpcGenericTrades.json(shop.quotes(),shop.npc().getNpcInventory().cargoSnapshot(),
            NpcInventoryConfig.forLoadout(shop.npc().getTemplateId()),player.registryAccess());
        // ADM's existing protocol bound is retained; no artificial offer count or paging policy.
        if(json.length()>30000) throw new IllegalArgumentException("Trade file exceeds ADM packet limit");
        if(refresh) PacketDistributor.sendToPlayer(player,new mod.cdv.tnpc.network.TradeRefreshPacket(shop.npc().getUUID(),shop.file(),json));
        else PacketDistributor.sendToPlayer(player,new OpenNpcTradePacket(shop.npc().getUUID(),shop.npc().getDisplayName().getString(),shop.file(),json));
    }
    public static void message(ServerPlayer player, String text) { player.displayClientMessage(Component.literal(text), true); }
}
