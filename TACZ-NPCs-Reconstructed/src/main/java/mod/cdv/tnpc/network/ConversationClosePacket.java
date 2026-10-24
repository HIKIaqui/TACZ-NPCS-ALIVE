package mod.cdv.tnpc.network;

import java.util.UUID;
import mod.cdv.tnpc.client.ConversationScreens;
import mod.cdv.tnpc.dialogue.NpcConversations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConversationClosePacket(UUID npc, String dialogue) implements CustomPacketPayload {
    public static final Type<ConversationClosePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("tacznpcs", "conversation_close"));
    public static final StreamCodec<FriendlyByteBuf, ConversationClosePacket> STREAM_CODEC = StreamCodec.ofMember(
        ConversationClosePacket::write, buf -> new ConversationClosePacket(buf.readUUID(), buf.readUtf(256)));
    private void write(FriendlyByteBuf buf) { buf.writeUUID(npc); buf.writeUtf(dialogue, 256); }
    @Override public Type<ConversationClosePacket> type() { return TYPE; }
    public static void handle(ConversationClosePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow() == PacketFlow.CLIENTBOUND) ConversationScreens.close(packet);
            else if (context.player() instanceof ServerPlayer player) NpcConversations.clientClosed(player, packet.npc(), packet.dialogue());
        });
    }
}
