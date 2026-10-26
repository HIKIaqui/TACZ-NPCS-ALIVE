package mod.cdv.tnpc.companion;

import java.util.*;
import com.google.gson.*;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.inventory.InventoryExchange;
import mod.cdv.tnpc.reputation.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Persistent ownership and orders. No chunk tickets, remote simulation, or client-owned inventory changes. */
public final class Companions {
    private static final String KEY="tnpcCompanion";
    public static UUID owner(AutoGunOperator npc) { var t=npc.getPersistentData().getCompound(KEY);return t.hasUUID("owner") ? t.getUUID("owner") : null; }
    public static boolean active(AutoGunOperator npc) { return owner(npc)!=null; }
    public static boolean owns(AutoGunOperator npc,ServerPlayer p) { return p.getUUID().equals(owner(npc)); }
    public static boolean waiting(AutoGunOperator npc) { return active(npc) && npc.getPersistentData().getCompound(KEY).getBoolean("waiting"); }
    public static Vec3 anchor(AutoGunOperator npc) { var t=npc.getPersistentData().getCompound(KEY);return new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z")); }
    public static ServerPlayer leader(AutoGunOperator npc) { var id=owner(npc);return id==null || npc.level().getServer()==null ? null : npc.level().getServer().getPlayerList().getPlayer(id); }
    public static void dismiss(AutoGunOperator npc) { npc.getPersistentData().remove(KEY);npc.getNavigation().stop();npc.setSprinting(false); }
    public static void tick(AutoGunOperator npc) {
        if(!active(npc) || npc.tickCount%20!=0) return;
        var p=leader(npc);
        if(p!=null && (npc.getTarget()==p || npc.isOpposedTo(p))) dismiss(npc);
    }
    private static boolean authorized(AutoGunOperator npc,ServerPlayer p) { return mod.cdv.tnpc.dialogue.NpcConversations.partner(npc)==p; }
    /** Validates state again at click time; an old menu cannot claim another player's companion. */
    public static boolean order(AutoGunOperator npc,ServerPlayer p,String id) {
        if(!authorized(npc,p)) return false;
        if(id.equals("tnpc_companion_join")) {
            if(active(npc) || !ReputationConfig.settings().allied(ReputationAccess.effective(npc,p))) return false;
            var t=new CompoundTag();t.putUUID("owner",p.getUUID());t.putBoolean("waiting",false);npc.getPersistentData().put(KEY,t);npc.getNavigation().stop();npc.endResting();return true;
        }
        if(!owns(npc,p)) return false;
        return switch(id) {
            case "tnpc_companion_wait" -> { var t=npc.getPersistentData().getCompound(KEY);t.putBoolean("waiting",true);t.putDouble("x",npc.getX());t.putDouble("y",npc.getY());t.putDouble("z",npc.getZ());npc.getNavigation().stop();yield true; }
            case "tnpc_companion_resume" -> { npc.getPersistentData().getCompound(KEY).putBoolean("waiting",false);npc.getNavigation().stop();yield true; }
            case "tnpc_companion_leave" -> { dismiss(npc);yield true; }
            case "tnpc_companion_equip" -> equip(npc,p);
            default -> false;
        };
    }
    private static boolean equip(AutoGunOperator npc,ServerPlayer p) {
        var incoming=p.getMainHandItem();if(incoming.isEmpty() || !npc.canHoldItem(incoming)) return false;
        EquipmentSlot slot=npc.getEquipmentSlotForItem(incoming);if(slot==EquipmentSlot.BODY) return false;
        // Explicit orders may downgrade equipment. Transfer ONE item and preserve the rest of the hand stack.
        var replacement=incoming.copyWithCount(1);var previous=npc.getItemBySlot(slot).copy();
        var slots=InventoryExchange.copy(p.getInventory().items);slots.get(p.getInventory().selected).shrink(1);
        if(!previous.isEmpty() && !InventoryExchange.insertPlayer(slots,previous)) return false;
        npc.setItemSlot(slot,replacement);
        if(slot==EquipmentSlot.MAINHAND) npc.refreshOrderedEquipment();
        for(int i=0;i<slots.size();i++) p.getInventory().setItem(i,slots.get(i));
        p.getInventory().setChanged();p.containerMenu.broadcastChanges();return true;
    }
    private static JsonObject action(String text,String id) { var c=new JsonObject();c.addProperty("text",text);c.addProperty("id",id);c.addProperty("close",true);return c; }
    private static JsonObject link(String text,String next) { var c=new JsonObject();c.addProperty("text",text);c.addProperty("next",next);return c; }
    public static String decorate(String json,AutoGunOperator npc,ServerPlayer p) {
        var root=JsonParser.parseString(json).getAsJsonObject();var nodes=root.getAsJsonObject("nodes");String start=root.get("start").getAsString();
        for(var entry:new ArrayList<>(nodes.entrySet())) {
            if(!entry.getValue().isJsonObject()) continue;var n=entry.getValue().getAsJsonObject();if(!n.has("choices")) continue;
            for(var raw:n.getAsJsonArray("choices")) {
                var choice=raw.getAsJsonObject();if(!choice.has("text") || !choice.get("text").getAsString().equals("Want to travel with me?") || !choice.has("next")) continue;
                var reply=nodes.getAsJsonObject(choice.get("next").getAsString());if(reply==null) continue;
                var back=reply.getAsJsonArray("choices").deepCopy();var choices=new JsonArray();
                if(owns(npc,p)) reply.addProperty("text","I'm already with you. What are your orders?");
                else if(active(npc)) reply.addProperty("text","I'm already travelling with someone else.");
                else if(ReputationConfig.settings().allied(ReputationAccess.effective(npc,p))) { reply.addProperty("text","Sure. We can watch each other's backs.");choices.add(action("Come with me.","tnpc_companion_join")); }
                else reply.addProperty("text","I need to know I can trust you first.");
                back.forEach(choices::add);reply.add("choices",choices);
            }
        }
        if(owns(npc,p)) {
            String id="tnpc_orders";while(nodes.has(id))id="_"+id;
            var orders=new JsonObject();orders.addProperty("text",waiting(npc) ? "I'm holding this position. What do you need?" : "What are your orders?");
            var choices=new JsonArray();choices.add(waiting(npc) ? action("Follow me.","tnpc_companion_resume") : action("Wait here.","tnpc_companion_wait"));
            choices.add(action("Give Equipment.","tnpc_companion_equip"));choices.add(action("Stop following me.","tnpc_companion_leave"));choices.add(link("Back.",start));orders.add("choices",choices);nodes.add(id,orders);
            var menu=nodes.getAsJsonObject(start).getAsJsonArray("choices");var updated=new JsonArray();updated.add(link("Orders.",id));menu.forEach(updated::add);nodes.getAsJsonObject(start).add("choices",updated);
        }
        String result=root.toString();if(result.length()>30000)throw new IllegalArgumentException("Companion dialogue exceeds ADM packet limit");return result;
    }
}
