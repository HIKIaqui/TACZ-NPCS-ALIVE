// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import mod.cdv.tnpc.client.FakePlayerPlayerAnimatorEvent;
import mod.cdv.tnpc.client.render.AutoGunOperatorRenderer;
import mod.cdv.tnpc.data.NpcBehaviorConfig;
import mod.cdv.tnpc.data.TemplateDataManager;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.entity.EntityEvents;
import mod.cdv.tnpc.entity.EntityRegistry;
import mod.cdv.tnpc.network.NetworkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

@Mod(value="tacznpcs")
public class TNPC {
    public static final String MODID = "tacznpcs";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TNPC(IEventBus modEventBus, ModContainer modContainer) {
        EntityRegistry.ENTITY_TYPES.register(modEventBus);
        mod.cdv.tnpc.campfire.CampfirePois.TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.register(new mod.cdv.tnpc.campfire.CampfireEvents());
        NeoForge.EVENT_BUS.register(new mod.cdv.tnpc.entity.DamageEscapeEvents());
        NeoForge.EVENT_BUS.register(new mod.cdv.tnpc.offline.OfflineEvents());
        NeoForge.EVENT_BUS.register(new mod.cdv.tnpc.travel.TravelEvents());
        EntityEvents.init(modEventBus);
        NeoForge.EVENT_BUS.register(this);
        NetworkManager.register(modEventBus);
        mod.cdv.tnpc.dialogue.NpcConversations.registerConditions();
        NeoForge.EVENT_BUS.register(new mod.cdv.tnpc.dialogue.NpcConversations());
    }

    @SubscribeEvent
    public void serverLoading(ServerAboutToStartEvent event) {
        mod.cdv.tnpc.data.NpcConfigExamples.install(event.getServer());
        if (!TemplateDataManager.init(event.getServer()))
            throw new IllegalStateException("Invalid NPC loadout config: replace aligned/opposed with faction and relations. See server log.");
        if (!mod.cdv.tnpc.reputation.ReputationConfig.load(event.getServer()))
            throw new IllegalStateException("Invalid tacznpc_reputation.json; see server log.");
        mod.cdv.tnpc.inventory.NpcInventoryConfig.load(event.getServer());
        NpcBehaviorConfig.load(event.getServer());
        mod.cdv.tnpc.offline.OfflineConfig.load(event.getServer());
        mod.cdv.tnpc.quest.QuestConfig.load(event.getServer());
        mod.cdv.tnpc.travel.TravelConfig.load(event.getServer());
        mod.cdv.tnpc.dialogue.NpcConversations.load(event.getServer());
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        mod.cdv.tnpc.offline.OfflineEvents.registerCommands(event);
        mod.cdv.tnpc.inventory.NpcInventoryCommands.register(event);
        mod.cdv.tnpc.quest.QuestCommands.register(event);
        mod.cdv.tnpc.reputation.ReputationCommands.register(event);
        mod.cdv.tnpc.travel.TravelCommands.register(event);
        CommandDispatcher dispatcher = event.getDispatcher();
        dispatcher.register((LiteralArgumentBuilder)Commands.literal((String)"tacznpcs_reload").requires(source -> source.hasPermission(2)).executes(context -> {
            if (!mod.cdv.tnpc.travel.TravelConfig.load(context.getSource().getServer()))
                context.getSource().sendFailure(Component.literal("Travel reload failed; previous settings retained."));
            boolean templatesLoaded = TemplateDataManager.init(context.getSource().getServer());
            if (!templatesLoaded) context.getSource().sendFailure(Component.literal("NPC templates reload failed; previous settings retained. Check faction/relations schema."));
            if (!mod.cdv.tnpc.reputation.ReputationConfig.load(context.getSource().getServer()))
                context.getSource().sendFailure(Component.literal("NPC reputation reload failed; previous settings retained."));
            if (!mod.cdv.tnpc.quest.QuestConfig.load(context.getSource().getServer()))
                ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Could not reload NPC quests; keeping previous settings."));
            else ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("NPC quests reloaded"), false);
            if (!mod.cdv.tnpc.inventory.NpcInventoryConfig.load(((CommandSourceStack)context.getSource()).getServer()))
                ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Could not reload NPC inventory settings."));
            if (!mod.cdv.tnpc.offline.OfflineConfig.load(((CommandSourceStack)context.getSource()).getServer()))
                ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Could not reload offlineActions; keeping previous settings."));
            boolean dialogueLoaded = mod.cdv.tnpc.dialogue.NpcConversations.load(((CommandSourceStack)context.getSource()).getServer());
            if (!dialogueLoaded) ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Could not reload NPC dialogues; keeping previous settings. Check the log."));
            else ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("NPC dialogues reloaded"), false);
            boolean behaviorLoaded = NpcBehaviorConfig.load(((CommandSourceStack)context.getSource()).getServer());
            if (behaviorLoaded) {
                ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("tacznpc_behavior.json reloaded"), false);
            } else {
                ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Could not reload tacznpc_behavior.json; keeping previous settings. Check the log."));
            }
            if (templatesLoaded) context.getSource().sendSuccess(() -> Component.literal("NPC loadouts, factions and structures reloaded"), false);
            return 1;
        }));
    }

    @EventBusSubscriber(modid="tacznpcs", bus=EventBusSubscriber.Bus.MOD, value={Dist.CLIENT})
    public static final class ClientEventHandler {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(AutoGunOperator.NPC_TYPE, AutoGunOperatorRenderer::new);
        }

        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Pre event) {
            if (ModList.get().isLoaded("playeranimator")) {
                FakePlayerPlayerAnimatorEvent.animateTick();
            }
        }
    }
}

