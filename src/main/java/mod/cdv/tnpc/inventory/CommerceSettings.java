package mod.cdv.tnpc.inventory;

import com.google.gson.*;
import java.math.*;
import java.util.*;
import mod.cdv.tnpc.reputation.ReputationConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

/** Currency list is global; tier price changes inherit through inventory defaults/loadouts. */
public final class CommerceSettings {
    public record Currency(String item,long value) {}
    public static final List<Currency> DEFAULT_CURRENCIES=List.of(new Currency("minecraft:emerald",1),
        new Currency("minecraft:emerald_block",60),new Currency("minecraft:netherite_block",3600));
    public static final Map<String,BigDecimal> DEFAULT_PERCENT=Map.of("villainized",new BigDecimal("100"),
        "hostile",new BigDecimal("50"),"hated",new BigDecimal("25"),"neutral",BigDecimal.ZERO,
        "friendly",new BigDecimal("-5"),"allied",new BigDecimal("-10"),"idolized",new BigDecimal("-20"));
    public static List<Currency> currencies(JsonObject root) {
        if(!root.has("commerce")) return DEFAULT_CURRENCIES;
        var o=root.getAsJsonObject("commerce"); keys(o,Set.of("currencies"));
        if(!o.has("currencies")) return DEFAULT_CURRENCIES;
        List<Currency> result=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for(var entry:o.getAsJsonArray("currencies")) {
            var c=entry.getAsJsonObject(); keys(c,Set.of("item","value"));
            if(!c.has("item") || !c.has("value") || !c.get("item").isJsonPrimitive() || !c.getAsJsonPrimitive("item").isString()
                || !c.get("value").isJsonPrimitive() || !c.getAsJsonPrimitive("value").isNumber()) throw new IllegalArgumentException("Currency requires item and integer value");
            String id=c.get("item").getAsString(); long value=c.get("value").getAsBigDecimal().longValueExact();
            if(!id.contains(":") || ResourceLocation.tryParse(id)==null || id.equals("minecraft:air") || !ids.add(id)
                || value<1 || value>1_000_000_000L) throw new IllegalArgumentException("Invalid/duplicate currency: "+id);
            result.add(new Currency(id,value));
        }
        if(result.isEmpty()) throw new IllegalArgumentException("At least one currency is required");
        return List.copyOf(result);
    }
    public static Map<String,BigDecimal> percentages(JsonObject o,Map<String,BigDecimal> fallback) {
        if(!o.has("reputationPricePercent")) return fallback;
        Map<String,BigDecimal> values=new HashMap<>(fallback);
        Set<String> seen=new HashSet<>();
        for(var e:o.getAsJsonObject("reputationPricePercent").entrySet()) {
            String tier=ReputationConfig.canonicalTier(e.getKey());
            if(!seen.add(tier)) throw new IllegalArgumentException("Duplicate reputation price tier: "+tier);
            if(!DEFAULT_PERCENT.containsKey(tier) || !e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Unknown/non-numeric price tier: "+e.getKey());
            BigDecimal n=e.getValue().getAsBigDecimal();
            if(n.compareTo(new BigDecimal("-100"))<=0 || n.compareTo(new BigDecimal("10000"))>0 || n.scale()>4)
                throw new IllegalArgumentException("Price percent must be > -100 and <= 10000, at most four decimal places");
            values.put(tier,n);
        }
        return Map.copyOf(values);
    }
    /** Integer prices round down; never create a zero-price offer. */
    public static long price(int base,long reputation,Map<String,BigDecimal> percent) {
        if(base<1) throw new IllegalArgumentException("Positive unit base price required");
        return Math.max(1,BigDecimal.valueOf(base).multiply(BigDecimal.ONE.add(percent.get(ReputationConfig.tier(reputation)).movePointLeft(2)))
            .setScale(0,RoundingMode.FLOOR).longValueExact());
    }
    public static CurrencySolver solver(List<Currency> currencies) {
        List<CurrencySolver.Coin> coins=new ArrayList<>();
        for(var currency:currencies) {
            var id=ResourceLocation.parse(currency.item()); Item item=BuiltInRegistries.ITEM.get(id);
            if(item==Items.AIR) throw new IllegalArgumentException("Unknown currency item: "+id);
            coins.add(new CurrencySolver.Coin(currency.item(),currency.value(),Math.min(999,item.getDefaultMaxStackSize())));
        }
        return new CurrencySolver(coins);
    }
    private static void keys(JsonObject o,Set<String> allowed) {
        for(String key:o.keySet()) if(!allowed.contains(key)) throw new IllegalArgumentException("Unknown commerce setting: "+key);
    }
}
