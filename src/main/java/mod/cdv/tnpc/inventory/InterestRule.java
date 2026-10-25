package mod.cdv.tnpc.inventory;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;

/** Priority and unit price are independent. Exact item selectors outrank tags. */
public record InterestRule(String selector,double weight,int basePrice,boolean protectedItem) {
    public boolean tag() { return selector.startsWith("#"); }
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var id=ResourceLocation.parse(tag() ? selector.substring(1) : selector);
        return tag() ? stack.is(TagKey.create(Registries.ITEM,id)) : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }
    public static InterestRule parse(JsonElement value) {
        String selector; double weight=1; int price=1; boolean protect=false;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) selector=value.getAsString();
        else {
            var o=value.getAsJsonObject();
            for(String key:o.keySet()) if(!Set.of("item","tag","weight","basePrice","protected").contains(key)) throw new IllegalArgumentException("Unknown interest field: "+key);
            if(o.has("item")==o.has("tag")) throw new IllegalArgumentException("Interest requires exactly one item or tag");
            var id=o.get(o.has("item") ? "item" : "tag");
            if(!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Interest selector must be a string");
            if(id.getAsString().startsWith("#")) throw new IllegalArgumentException("Object selectors omit #; use the tag field");
            selector=(o.has("tag") ? "#" : "")+id.getAsString();
            if(o.has("weight")) {
                if(!o.get("weight").isJsonPrimitive() || !o.get("weight").getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("weight must be numeric");
                weight=o.get("weight").getAsDouble();
            }
            if(o.has("basePrice")) {
                if(!o.get("basePrice").isJsonPrimitive() || !o.get("basePrice").getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("basePrice must be numeric");
                price=o.get("basePrice").getAsBigDecimal().intValueExact();
            }
            if(o.has("protected")) {
                if(!o.get("protected").isJsonPrimitive() || !o.get("protected").getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("protected must be boolean");
                protect=o.get("protected").getAsBoolean();
            }
        }
        String raw=selector.startsWith("#") ? selector.substring(1) : selector;
        if(!raw.contains(":") || ResourceLocation.tryParse(raw)==null || raw.equals("minecraft:air") || raw.startsWith("#")) throw new IllegalArgumentException("Invalid interest selector: "+selector);
        if(!Double.isFinite(weight) || weight<0 || weight>1_000_000 || price<1 || price>1_000_000_000) throw new IllegalArgumentException("Interest weight/price out of range");
        return new InterestRule(selector,weight,price,protect);
    }
}
