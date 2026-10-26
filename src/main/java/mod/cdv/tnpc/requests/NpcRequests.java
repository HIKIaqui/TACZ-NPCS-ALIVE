package mod.cdv.tnpc.requests;

import com.google.gson.*;
import java.util.*;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.inventory.*;
import mod.cdv.tnpc.reputation.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

/** One persistent request per NPC. Only the server resolves items, quantities and rewards. */
public final class NpcRequests {
    private static final String KEY="tnpcRequest", NEXT="tnpcRequestNext";
    private static RequestConfig.Settings settings=RequestConfig.DEFAULT;
    public record Request(UUID token,String loadout,String item,int count,int personal,int faction,int cooldown,Map<String,Integer> coins) { public Request { coins=Collections.unmodifiableMap(new LinkedHashMap<>(coins)); } }
    private record Candidate(Item item,double weight,int count) {}
    public static void configure(RequestConfig.Settings value) { settings=value; }
    private static RequestConfig.Profile policy(AutoGunOperator npc) { return settings.forLoadout(npc.getTemplateId()); }
    private static long owned(AutoGunOperator npc,Item item) {
        return npc.getNpcInventory().allItems().stream().filter(s->s.is(item)).mapToLong(ItemStack::getCount).sum();
    }
    private static double mean(NpcInventoryConfig.Profile p) { return RequestSelection.mean(p.interests().stream().mapToDouble(InterestRule::weight).toArray()); }
    private static Item resolve(String id) { var key=ResourceLocation.tryParse(id); return key==null ? Items.AIR : BuiltInRegistries.ITEM.get(key); }
    public static Request current(AutoGunOperator npc) {
        var t=npc.getPersistentData().getCompound(KEY);
        if(!t.hasUUID("token")) return null;
        try {
            if(t.getInt("version")==0) return upgrade(npc,t);
            if(t.getInt("version")!=2) return null;
            Map<String,Integer> coins=new LinkedHashMap<>();
            for(var raw:t.getList("coins",net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                var coin=(CompoundTag)raw;String id=coin.getString("item");int count=coin.getInt("count");
                if(resolve(id)==Items.AIR || count<1 || count>999 || coins.put(id,count)!=null) return null;
            }
            if(coins.size()>3) return null;
            var r=new Request(t.getUUID("token"),t.getString("loadout"),t.getString("item"),t.getInt("count"),t.getInt("personal"),t.getInt("faction"),t.getInt("cooldown"),coins);
            if(r.count()<1 || r.count()>8 || r.personal()<0 || r.personal()>1000000000 || r.faction()<0 || r.faction()>1000000 || r.cooldown()<0 || r.cooldown()>1728000) return null;
            return r;
        } catch(RuntimeException invalid) { return null; }
    }
    private static void save(AutoGunOperator npc,Request r) {
        var t=new CompoundTag(); t.putInt("version",2); t.putUUID("token",r.token()); t.putString("loadout",r.loadout());t.putString("item",r.item());
        t.putInt("count",r.count());t.putInt("personal",r.personal());t.putInt("faction",r.faction());t.putInt("cooldown",r.cooldown());
        var coins=new net.minecraft.nbt.ListTag();r.coins().forEach((id,count)->{var c=new CompoundTag();c.putString("item",id);c.putInt("count",count);coins.add(c);});t.put("coins",coins);
        npc.getPersistentData().put(KEY,t);
    }
    private static Request quote(AutoGunOperator npc,UUID token,Item item,int count) {
        var p=NpcInventoryConfig.forLoadout(npc.getTemplateId());var rule=policy(npc);var stack=new ItemStack(item);
        long value=RequestSelection.rewardValue(p.basePrice(stack).orElseThrow(),count,rule.currencyRewardMultiplier());
        var coins=value==0 ? Map.<String,Integer>of() : CommerceSettings.solver(NpcInventoryConfig.currencies()).solve(value).counts();
        return new Request(token,npc.getTemplateId(),BuiltInRegistries.ITEM.getKey(item).toString(),count,
            RequestSelection.reputation(rule.personalReward(),mean(p),p.interest(stack)),rule.factionReward(),rule.cooldownTicks(),coins);
    }
    private static Request upgrade(AutoGunOperator npc,CompoundTag old) {
        Item item=resolve(old.getString("item"));int count=old.getInt("count");
        if(!old.hasUUID("token") || item==Items.AIR || count<1 || count>8 || !old.getString("loadout").equals(npc.getTemplateId())) return null;
        var r=quote(npc,old.getUUID("token"),item,count);save(npc,r);return r;
    }
    private static boolean useful(AutoGunOperator npc,Request r) {
        Item item=resolve(r.item()); if(item==Items.AIR || !r.loadout().equals(npc.getTemplateId())) return false;
        var p=NpcInventoryConfig.forLoadout(npc.getTemplateId()); var stack=new ItemStack(item);
        return RequestSelection.chance(mean(p),p.interest(stack))>0 && RequestSelection.needed(stack.getMaxStackSize(),owned(npc,item))>0;
    }
    public static Request ensure(AutoGunOperator npc) {
        if(!policy(npc).enabled()) return null;
        var r=current(npc); if(r!=null && useful(npc,r)) return r;
        npc.getPersistentData().remove(KEY);
        if(npc.level().getGameTime()<npc.getPersistentData().getLong(NEXT)) return null;
        var p=NpcInventoryConfig.forLoadout(npc.getTemplateId()); double mean=mean(p);
        if(mean<=0) return null;
        List<Candidate> candidates=new ArrayList<>();
        Map<Item,Long> stock=new HashMap<>();
        for(var stack:npc.getNpcInventory().allItems()) stock.merge(stack.getItem(),(long)stack.getCount(),Long::sum);
        var cargo=npc.getNpcInventory().cargoSnapshot();
        for(Item item:BuiltInRegistries.ITEM) if(item!=Items.AIR) {
            var stack=new ItemStack(item); double weight=p.interest(stack); int count=RequestSelection.needed(stack.getMaxStackSize(),stock.getOrDefault(item,0L));
            if(count>0 && RequestSelection.chance(mean,weight)>0
                && CargoPlanner.plan(cargo,stack.copyWithCount(count),p.maxCargoSlots(),s->0,s->true,false,()->0).accepted()==count)
                candidates.add(new Candidate(item,weight,count));
        }
        var chosen=RequestSelection.pick(candidates,Candidate::weight,mean,npc.getRandom().nextDouble());
        if(chosen==null) return null;
        r=quote(npc,UUID.randomUUID(),chosen.item(),chosen.count());
        save(npc,r); return r;
    }
    private static boolean eligible(AutoGunOperator npc,ServerPlayer p) {
        return policy(npc).enabled() && !npc.isOpposedTo(p) && ReputationAccess.effective(npc,p)>=policy(npc).minimumReputation();
    }
    public static String decorate(String json,AutoGunOperator npc,ServerPlayer player) {
        var root=JsonParser.parseString(json).getAsJsonObject(); var nodes=root.getAsJsonObject("nodes");
        JsonObject help=null;
        for(var e:nodes.entrySet()) if(e.getValue().isJsonObject()) {
            var node=e.getValue().getAsJsonObject(); if(!node.has("choices")) continue;
            for(var raw:node.getAsJsonArray("choices")) {
                var c=raw.getAsJsonObject(); if(c.has("text") && c.get("text").getAsString().equals("Need help with something?") && c.has("next")) help=nodes.getAsJsonObject(c.get("next").getAsString());
            }
        }
        if(help==null) return json;
        Request r=eligible(npc,player) ? ensure(npc) : null;
        if(r!=null) {
            String name=new ItemStack(resolve(r.item())).getHoverName().getString();
            String payment=r.coins().entrySet().stream().map(e->e.getValue()+" x "+new ItemStack(resolve(e.getKey())).getHoverName().getString()).collect(java.util.stream.Collectors.joining(", "));
            help.addProperty("text","Could you bring me "+r.count()+" x "+name+"?"+(payment.isBlank() ? " I'd appreciate the help." : " I can offer you "+payment+"."));
            var choices=help.getAsJsonArray("choices").deepCopy(); var updated=new JsonArray();var delivery=new JsonObject();
            delivery.addProperty("id","tnpc_request_"+r.token()); delivery.addProperty("text","Deliver "+r.count()+" x "+name+"."); delivery.addProperty("close",true); updated.add(delivery); choices.forEach(updated::add); help.add("choices",updated);
        }
        String result=root.toString(); if(result.length()>30000) throw new IllegalArgumentException("Request dialogue exceeds ADM packet limit");return result;
    }
    /** Copy-only transfer plan: all item components survive; no partial delivery or discarded overflow. */
    public static InventoryExchange.Plan plan(List<ItemStack> player,List<ItemStack> cargo,Item item,int count,int capacity) {
        if(count<1 || item==Items.AIR) return null;
        var p=InventoryExchange.copy(player);var n=InventoryExchange.copy(cargo);int remaining=count;
        for(var stack:p) if(stack.is(item) && remaining>0) { int take=Math.min(remaining,stack.getCount());InventoryExchange.insert(n,stack.split(take));remaining-=take; }
        return remaining>0 || n.size()>capacity ? null : new InventoryExchange.Plan(p,n);
    }
    public static boolean deliver(AutoGunOperator npc,ServerPlayer player,String id) {
        if(mod.cdv.tnpc.dialogue.NpcConversations.partner(npc)!=player || !eligible(npc,player)) return false;
        var r=current(npc);
        if(r==null || !id.equals("tnpc_request_"+r.token()) || !useful(npc,r)) return false;
        var cargo=npc.getNpcInventory().cargoSnapshot();
        var transfer=plan(player.getInventory().items,cargo,resolve(r.item()),r.count(),Math.max(cargo.size(),NpcInventoryConfig.forLoadout(npc.getTemplateId()).maxCargoSlots()));
        if(transfer==null || !npc.getNpcInventory().canReplaceCargo(transfer.npc())) return false;
        // Quest currency is generated as a completion reward, rather than taken from NPC trade stock.
        for(var coin:r.coins().entrySet()) if(!InventoryExchange.insertPlayer(transfer.player(),new ItemStack(resolve(coin.getKey()),coin.getValue()))) return false;
        var data=ReputationData.get(player.server);
        int beforePersonal=data.personal(player.getUUID(),npc.getUUID()),beforeFaction=data.faction(player.getUUID(),npc.getFactionId());
        int personal=ReputationData.add(data.personal(player.getUUID(),npc.getUUID()),r.personal());
        int faction=ReputationData.add(data.faction(player.getUUID(),npc.getFactionId()),r.faction());
        npc.getNpcInventory().replaceCargo(transfer.npc());
        for(int i=0;i<transfer.player().size();i++) player.getInventory().setItem(i,transfer.player().get(i));
        npc.getPersistentData().remove(KEY);npc.getPersistentData().putLong(NEXT,npc.level().getGameTime()+r.cooldown());
        if(r.personal()>0) data.personal(player.getUUID(),npc.getUUID(),personal);
        if(r.faction()>0) data.faction(player.getUUID(),npc.getFactionId(),faction);
        player.getInventory().setChanged();player.containerMenu.broadcastChanges();
        String feedback="Supplies delivered. Thank you!";
        if(personal>beforePersonal) feedback+=" | Personal reputation +"+(personal-beforePersonal);
        if(faction>beforeFaction) feedback+=" | Faction reputation +"+(faction-beforeFaction);
        NpcInventoryTrades.message(player,feedback);
        return true;
    }
}
