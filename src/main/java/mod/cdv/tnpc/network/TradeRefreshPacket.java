package mod.cdv.tnpc.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Updates the existing screen so a stock refresh cannot trigger its close handshake. */
public record TradeRefreshPacket(UUID npc,String file,String json) implements CustomPacketPayload {
    public static final Type<TradeRefreshPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("tacznpcs","trade_refresh"));
    public static final StreamCodec<FriendlyByteBuf,TradeRefreshPacket> STREAM_CODEC=StreamCodec.ofMember(
        TradeRefreshPacket::write,b->new TradeRefreshPacket(b.readUUID(),b.readUtf(256),b.readUtf(30000)));
    private void write(FriendlyByteBuf b) { b.writeUUID(npc); b.writeUtf(file,256); b.writeUtf(json,30000); }
    @Override public Type<TradeRefreshPacket> type() { return TYPE; }
    public static void handle(TradeRefreshPacket p,IPayloadContext context) {
        context.enqueueWork(()->mod.cdv.tnpc.client.ConversationScreens.refresh(p));
    }
}
