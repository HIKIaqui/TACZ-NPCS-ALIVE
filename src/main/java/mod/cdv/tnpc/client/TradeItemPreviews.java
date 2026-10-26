package mod.cdv.tnpc.client;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.aviel.dialogue.npc.trade.NpcTradeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Identity keys avoid conflating two ADM TradeItem records with identical IDs and different components. */
public final class TradeItemPreviews {
    private static final Map<NpcTradeDefinition.TradeItem,ItemStack> previews=new IdentityHashMap<>();
    public static void bind(String file,String json,NpcTradeDefinition definition) {
        previews.clear();
        if(!file.startsWith("tnpc_stock_") || definition==null || Minecraft.getInstance().level==null) return;
        var ops=Minecraft.getInstance().level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var root=JsonParser.parseString(json).getAsJsonObject();
        int index=0;
        for(var entry:root.getAsJsonArray("offers")) {
            var object=entry.getAsJsonObject();
            var offer=definition.offerByIdOrIndex(object.get("id").getAsString(),index++);
            if(offer!=null && offer.result().size()==1 && object.has("tnpcStack"))
                previews.put(offer.result().getFirst(),ItemStack.CODEC.parse(ops,object.get("tnpcStack")).getOrThrow());
        }
    }
    public static ItemStack get(NpcTradeDefinition.TradeItem item) {
        var stack=previews.get(item); return stack==null ? null : stack.copy();
    }
}
