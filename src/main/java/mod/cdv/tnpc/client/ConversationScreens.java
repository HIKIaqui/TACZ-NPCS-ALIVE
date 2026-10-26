package mod.cdv.tnpc.client;

import mod.cdv.tnpc.network.ConversationClosePacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "tacznpcs", value = Dist.CLIENT)
public final class ConversationScreens {
    public static void refresh(mod.cdv.tnpc.network.TradeRefreshPacket packet) {
        if(Minecraft.getInstance().screen instanceof TradeScreenAccessor trade
            && trade.tnpc$tradeNpc().equals(packet.npc()) && trade.tnpc$tradeFile().equals(packet.file())) trade.tnpc$refresh(packet.json());
    }
    public static void close(ConversationClosePacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof TradeScreenAccessor trade && trade.tnpc$tradeNpc().equals(packet.npc())
            && trade.tnpc$tradeFile().equals(packet.dialogue())) client.setScreen(null);
        if (client.screen instanceof DialogueScreenAccessor screen
            && screen.tnpc$npcUuid().equals(packet.npc()) && screen.tnpc$dialogueFile().equals(packet.dialogue()))
            client.setScreen(null);
    }
    @SubscribeEvent public static void closing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof TradeScreenAccessor trade && Minecraft.getInstance().getConnection() != null)
            PacketDistributor.sendToServer(new ConversationClosePacket(trade.tnpc$tradeNpc(), trade.tnpc$tradeFile()));
        if (event.getScreen() instanceof DialogueScreenAccessor screen
            && screen.tnpc$dialogueFile().startsWith("tacznpcs:conversation/")
            && Minecraft.getInstance().getConnection() != null)
            PacketDistributor.sendToServer(new ConversationClosePacket(screen.tnpc$npcUuid(), screen.tnpc$dialogueFile()));
    }
}
