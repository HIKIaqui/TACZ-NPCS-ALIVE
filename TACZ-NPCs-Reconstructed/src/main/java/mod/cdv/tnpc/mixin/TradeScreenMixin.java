package mod.cdv.tnpc.mixin;

import java.util.UUID;
import mod.cdv.tnpc.client.TradeScreenAccessor;
import net.aviel.dialogue.client.NpcTradeScreen;
import org.spongepowered.asm.mixin.*;

@Mixin(value=NpcTradeScreen.class,remap=false)
public abstract class TradeScreenMixin implements TradeScreenAccessor {
    @Shadow @Final private UUID npcUuid;
    @Shadow @Final private String tradeFile;
    public UUID tnpc$tradeNpc() { return npcUuid; }
    public String tnpc$tradeFile() { return tradeFile; }
}
