// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.network;

import mod.cdv.tnpc.network.AnimationPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkManager {
    public static final String PROTOCOL_VERSION = "2";

    public static void register(IEventBus modBus) {
        modBus.addListener(NetworkManager::registerPackets);
    }

    private static void registerPackets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playBidirectional(ConversationClosePacket.TYPE, ConversationClosePacket.STREAM_CODEC, ConversationClosePacket::handle);
        registrar.playToClient(AnimationPacket.TYPE, AnimationPacket.STREAM_CODEC, AnimationPacket::handle);
    }
}

