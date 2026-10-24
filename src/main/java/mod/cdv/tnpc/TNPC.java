// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import mod.cdv.tnpc.client.FakePlayerPlayerAnimatorEvent;
import mod.cdv.tnpc.client.render.AutoGunOperatorRenderer;
import mod.cdv.tnpc.data.StructureTemplate;
import mod.cdv.tnpc.data.NpcBehaviorConfig;
import mod.cdv.tnpc.data.TemplateDataManager;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.entity.EntityEvents;
import mod.cdv.tnpc.entity.EntityRegistry;
import mod.cdv.tnpc.network.NetworkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
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
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
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
        EntityEvents.init(modEventBus);
        NeoForge.EVENT_BUS.register(this);
        NetworkManager.register(modEventBus);
        mod.cdv.tnpc.dialogue.NpcConversations.registerConditions();
        NeoForge.EVENT_BUS.register(new mod.cdv.tnpc.dialogue.NpcConversations());
    }

    @SubscribeEvent
    public void serverLoading(ServerAboutToStartEvent event) {
        mod.cdv.tnpc.data.NpcConfigExamples.install(event.getServer());
        TemplateDataManager.init(event.getServer());
        mod.cdv.tnpc.inventory.NpcInventoryConfig.load(event.getServer());
        NpcBehaviorConfig.load(event.getServer());
        mod.cdv.tnpc.offline.OfflineConfig.load(event.getServer());
        mod.cdv.tnpc.quest.QuestConfig.load(event.getServer());
        mod.cdv.tnpc.dialogue.NpcConversations.load(event.getServer());
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        mod.cdv.tnpc.offline.OfflineEvents.registerCommands(event);
        mod.cdv.tnpc.inventory.NpcInventoryCommands.register(event);
        mod.cdv.tnpc.quest.QuestCommands.register(event);
        CommandDispatcher dispatcher = event.getDispatcher();
        dispatcher.register((LiteralArgumentBuilder)Commands.literal((String)"tacznpcs_reload").executes(context -> {
            TemplateDataManager.init(((CommandSourceStack)context.getSource()).getServer());
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
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"npc_loadouts.json reloaded"), false);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal((String)"npc_factions.json reloaded"), false);
            return 1;
        }));
    }

    @EventBusSubscriber(modid="tacznpcs", bus=EventBusSubscriber.Bus.GAME)
    public static class StructureSpawnHandler {
        @SubscribeEvent
        public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
            if (event.getSpawnType() == MobSpawnType.STRUCTURE || event.getSpawnType() == MobSpawnType.NATURAL) {
                ServerLevel level = event.getLevel().getLevel();
                String originalEntityId = EntityType.getKey((EntityType)event.getEntity().getType()).toString();
                Vec3 spawnPos = new Vec3(event.getX(), event.getY(), event.getZ());
                BlockPos blockPos = BlockPos.containing((Position)spawnPos);
                for (Structure structure : level.structureManager().getAllStructuresAt(blockPos).keySet()) {
                    ResourceLocation structureLocation = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
                    if (structureLocation == null) continue;
                    String structureId = structureLocation.toString();
                    ArrayList<StructureTemplate> templateArr = TemplateDataManager.STRUCTURES.getOrDefault(structureId, new ArrayList());
                    for (StructureTemplate template : templateArr) {
                        if (!template.toReplace().contains(originalEntityId) || !(level.random.nextFloat() < template.chance())) continue;
                        AutoGunOperator op = AutoGunOperator.create((Level)level, template.loadout());
                        op.moveTo(spawnPos);
                        op.setXRot(event.getEntity().getXRot());
                        op.setYRot(event.getEntity().getYRot());
                        level.addFreshEntity((Entity)op);
                        event.getEntity().discard();
                        event.setSpawnCancelled(true);
                    }
                }
            }
        }
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

