package mod.cdv.tnpc.mixin;

import java.util.UUID;
import mod.cdv.tnpc.client.TradeScreenAccessor;
import net.aviel.dialogue.client.NpcTradeScreen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.aviel.dialogue.npc.trade.NpcTradeDefinition;

@Mixin(value=NpcTradeScreen.class,remap=false)
public abstract class TradeScreenMixin implements TradeScreenAccessor {
    @Shadow @Final private UUID npcUuid;
    @Shadow @Final private String tradeFile;
    @Shadow @Final @Mutable private NpcTradeDefinition definition;
    public void tnpc$refresh(String json) {
        definition=NpcTradeDefinition.fromJson(json);
        mod.cdv.tnpc.client.TradeItemPreviews.bind(tradeFile,json,definition);
    }
    @Inject(method="<init>",at=@At("TAIL"),remap=false)
    private void tnpc$bind(UUID npc,String name,String file,String json,CallbackInfo ci) {
        mod.cdv.tnpc.client.TradeItemPreviews.bind(file,json,definition);
    }
    public UUID tnpc$tradeNpc() { return npcUuid; }
    public String tnpc$tradeFile() { return tradeFile; }
}
