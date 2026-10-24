// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.network;

import io.netty.buffer.ByteBuf;
import mod.cdv.tnpc.client.ClientAnimationManager;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AnimationPacket(byte animation, int id) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<AnimationPacket> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath((String)"tacznpcs", (String)"animation"));
    public static final StreamCodec<ByteBuf, AnimationPacket> STREAM_CODEC = StreamCodec.of(AnimationPacket::encode, AnimationPacket::decode);
    public static final byte RELOAD = 0;
    public static final byte MELEE = 1;
    public static final byte DEATH = 2;

    private static void encode(ByteBuf buf, AnimationPacket packet) {
        buf.writeByte((int)packet.animation);
        buf.writeInt(packet.id);
    }

    private static AnimationPacket decode(ByteBuf buf) {
        return new AnimationPacket(buf.readByte(), buf.readInt());
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnimationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            switch (packet.animation) {
                case 0: {
                    ClientAnimationManager.reload(packet.id);
                    break;
                }
                case 1: {
                    ClientAnimationManager.melee(packet.id);
                    break;
                }
                case 2: {
                    ClientAnimationManager.die(packet.id);
                }
            }
        });
    }
}

