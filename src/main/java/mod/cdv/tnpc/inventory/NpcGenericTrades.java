package mod.cdv.tnpc.inventory;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;

/** Server-owned quotes for exact cargo variants; equipment and configured currencies never become goods. */
public final class NpcGenericTrades {
    public record Quote(String id,ItemStack prototype,CurrencySolver.Payment payment) {
        public Quote { prototype=prototype.copyWithCount(1); }
        @Override public ItemStack prototype() { return prototype.copy(); }
    }
    public record Shop(List<Quote> quotes,long reputation) {}
    public static Shop create(NpcInventoryConfig.Profile profile,List<CommerceSettings.Currency> currencies,
                              List<ItemStack> cargo,long reputation) {
        var solver=CommerceSettings.solver(currencies);
        Set<String> currencyIds=new HashSet<>(); currencies.forEach(c->currencyIds.add(c.item()));
        List<Quote> quotes=new ArrayList<>();
        for(var stack:cargo) {
            if(stack.isEmpty() || profile.protectedItem(stack) || profile.basePrice(stack).isEmpty()
                || currencyIds.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) continue;
            if(quotes.stream().anyMatch(q->ItemStack.isSameItemSameComponents(q.prototype(),stack))) continue;
            var payment=solver.solve(CommerceSettings.price(profile.basePrice(stack).orElseThrow(),reputation,profile.reputationPricePercent()));
            // No positive payable value below this price: hide, never grant free items or round up.
            if(payment.value()==0) continue;
            quotes.add(new Quote("stock_"+quotes.size(),stack,payment));
        }
        return new Shop(List.copyOf(quotes),reputation);
    }
    public static String json(Shop shop,List<ItemStack> cargo,NpcInventoryConfig.Profile profile,HolderLookup.Provider lookup) {
        JsonObject root=new JsonObject(); root.addProperty("title","Trade");
        root.addProperty("subtitle","Real stock | Reputation: "+shop.reputation()+" | Fixed payment; no change");
        JsonArray offers=new JsonArray(); root.add("offers",offers);
        var ops=lookup.createSerializationContext(JsonOps.INSTANCE);
        for(var quote:shop.quotes()) {
            var stack=quote.prototype(); long stock=InventoryExchange.stock(cargo,stack,s->!profile.protectedItem(s));
            JsonObject offer=new JsonObject(); offer.addProperty("id",quote.id());
            offer.addProperty("title",stack.getHoverName().getString()+" | Stock: "+stock);
            offer.addProperty("locked",stock<1 || profile.protectedItem(stack));
            JsonArray cost=new JsonArray();
            quote.payment().counts().forEach((id,count)->cost.add(item(id,count))); offer.add("cost",cost);
            offer.add("result",item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),1));
            // ADM only knows item IDs. Our client bridge renders this exact component-preserving preview.
            offer.add("tnpcStack",ItemStack.CODEC.encodeStart(ops,stack).getOrThrow());
            offers.add(offer);
        }
        return root.toString();
    }
    private static JsonObject item(String id,int count) {
        JsonObject item=new JsonObject(); item.addProperty("item",id); item.addProperty("count",count); return item;
    }
}
