package mod.cdv.tnpc.dialogue;

import com.mojang.logging.LogUtils;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.network.ConversationClosePacket;
import net.aviel.dialogue.api.AdmDialogueApi;
import net.aviel.dialogue.api.event.*;
import net.aviel.dialogue.npc.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/** Server owns availability and sessions. AI perception and combat always continue running. */
public final class NpcConversations {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static DialogueProfiles.Snapshot config;
    private record Talk(AutoGunOperator npc, ServerPlayer player,
                        DialogueSessionManager.Session adm, long expires) {}
    private static final Map<UUID, Talk> talks = new HashMap<>();
    private static AutoGunOperator openingNpc;
    private static ServerPlayer openingPlayer;

    public static void registerConditions() {
        AdmDialogueApi.registerConditionType("tnpc.can_trade", (p,e,c) -> e instanceof AutoGunOperator npc
            && mod.cdv.tnpc.inventory.NpcInventoryConfig.forLoadout(npc.getTemplateId()).canTrade());
        AdmDialogueApi.registerConditionType("tnpc.faction", (p, e, c) -> e instanceof AutoGunOperator npc
            && npc.getTemplate() != null && !c.id().isBlank() && npc.getTemplate().aligned().contains(c.id()));
        AdmDialogueApi.registerConditionType("tnpc.loadout", (p, e, c) -> e instanceof AutoGunOperator npc
            && !c.id().isBlank() && npc.getTemplateId().equals(c.id()));
        AdmDialogueApi.registerConditionType("tnpc.uuid", (p, e, c) -> e instanceof AutoGunOperator npc
            && npc.getUUID().toString().equals(c.id()));
        AdmDialogueApi.registerConditionType("tnpc.healthy", (p, e, c) -> e instanceof AutoGunOperator npc
            && (c.min() == null || npc.getHealth() / npc.getMaxHealth() >= c.min())
            && (c.max() == null || npc.getHealth() / npc.getMaxHealth() <= c.max()));
    }

    public static boolean load(MinecraftServer server) {
        Path configPath = server.getServerDirectory().resolve("config/tacznpc_dialogue.json");
        try {
            Files.createDirectories(configPath.getParent());
            installDefault(configPath, "tacznpc_dialogue.json");
            Path profiles = configPath.resolveSibling("tacznpc_dialogues");
            Files.createDirectories(profiles);
            installDefault(profiles.resolve("generic.json"), "generic.json");
            installDefault(profiles.resolve("example_faction.json"), "example_faction.json");
            DialogueProfiles.Snapshot candidate = DialogueProfiles.read(configPath);
            // All JSON is parsed before updating the active registry or ending conversations.
            candidate.dialogues().forEach((id,json) -> AdmDialogueApi.registerDialogue(id, mod.cdv.tnpc.inventory.NpcInventoryTrades.decorate(json)));
            for (Talk talk : List.copyOf(talks.values())) close(talk);
            if (config != null) for (String id : config.dialogues().keySet())
                if (!candidate.dialogues().containsKey(id)) AdmDialogueApi.unregisterDialogue(id);
            config = candidate;
            LOGGER.info("NPC dialogues loaded: {} rules", candidate.rules().size());
            return true;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not load NPC dialogues; keeping previous settings", exception);
            return false;
        }
    }
    private static void installDefault(Path path, String resource) throws IOException {
        if (Files.exists(path)) return;
        try (InputStream stream = NpcConversations.class.getResourceAsStream("/dialogue_defaults/" + resource)) {
            if (stream == null) throw new IOException("Missing bundled dialogue: " + resource);
            Files.copy(stream, path);
        }
    }

    public static InteractionResult interact(AutoGunOperator npc, ServerPlayer player) {
        if (npc.isOpposedTo(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("This NPC is not willing to talk to you."), false);
            return InteractionResult.PASS;
        }
        if (!available(npc, player) || talks.containsKey(npc.getUUID()) || talkingPlayer(player)) return InteractionResult.PASS;
        String dialogue = config.select(condition -> DialogueConditionEvaluator.matches(condition, player, npc),
            choice -> NpcDialogueService.isChoiceAvailable(player, npc, choice));
        if (dialogue == null) return InteractionResult.PASS;
        openingNpc = npc; openingPlayer = player;
        try { AdmDialogueApi.openDialogue(player, npc, dialogue); }
        finally { openingNpc = null; openingPlayer = null; }
        DialogueSessionManager.Session session = DialogueSessionManager.get(player);
        if (session == null || !session.targetUuid().equals(npc.getUUID()) || !session.dialogueFile().equals(dialogue))
            return InteractionResult.PASS;
        talks.put(npc.getUUID(), new Talk(npc, player, session, npc.level().getGameTime() + config.maxDurationTicks()));
        npc.getNavigation().stop();
        return InteractionResult.CONSUME;
    }
    private static boolean talkingPlayer(ServerPlayer player) {
        return talks.values().stream().anyMatch(t -> t.player() == player);
    }
    private static boolean available(AutoGunOperator npc, ServerPlayer player) {
        if (config == null || !config.enabled() || !npc.isAlive() || npc.isRemoved() || !player.isAlive()
            || player.isSpectator() || npc.level() != player.level() || npc.getTemplate() == null
            || npc.getTarget() != null || npc.isOnAlert() || npc.isOpposedTo(player) || npc.hurtTime > 0 || npc.isOnFire()
            || npc.getVehicle() != null || npc.isPassenger() || npc.isLeashed()
            || npc.distanceToSqr(player) > config.maxDistance() * config.maxDistance()
            || npc.distanceToSqr(player) > net.aviel.dialogue.Config.maxInteractDistanceSqr()) return false;
        if (npc.getPersistentData().contains("tnpcLastDamage")
            && npc.level().getGameTime() - npc.getPersistentData().getLong("tnpcLastDamage") < config.damageCooldownTicks()) return false;
        int lastDamage = npc.getLastHurtByMobTimestamp();
        return (lastDamage == 0 || npc.tickCount - lastDamage >= config.damageCooldownTicks())
            && npc.getSensing().hasLineOfSight(player);
    }
    public static ServerPlayer partner(AutoGunOperator npc) {
        Talk talk = talks.get(npc.getUUID());
        return talk != null && valid(talk) ? talk.player() : null;
    }
    public static boolean canTrade(AutoGunOperator npc, ServerPlayer player) {
        Talk talk = talks.get(npc.getUUID());
        return talk != null && talk.player() == player && available(npc,player)
            && npc.level().getGameTime() < talk.expires()
            && player.server.getPlayerList().getPlayer(player.getUUID()) == player
            && player.serverLevel().getEntity(npc.getUUID()) == npc;
    }
    private static boolean valid(Talk talk) {
        return (DialogueSessionManager.get(talk.player()) == talk.adm()
                || mod.cdv.tnpc.inventory.NpcInventoryTrades.active(talk.npc(),talk.player()))
            && talk.player().server.getPlayerList().getPlayer(talk.player().getUUID()) == talk.player()
            && talk.player().serverLevel().getEntity(talk.npc().getUUID()) == talk.npc()
            && talk.npc().level().getGameTime() < talk.expires() && available(talk.npc(), talk.player());
    }
    private static void close(Talk talk) {
        talks.remove(talk.npc().getUUID(), talk);
        mod.cdv.tnpc.inventory.NpcInventoryTrades.close(talk.npc(),talk.player());
        if (DialogueSessionManager.get(talk.player()) == talk.adm()) {
            DialogueSessionManager.close(talk.player());
            PacketDistributor.sendToPlayer(talk.player(), new ConversationClosePacket(talk.npc().getUUID(), talk.adm().dialogueFile()));
        }
    }
    public static void clientClosed(ServerPlayer player, UUID npc, String file) {
        Talk talk = talks.get(npc);
        if (talk == null || talk.player() != player) return;
        if (mod.cdv.tnpc.inventory.NpcInventoryTrades.closingTrade(talk.npc(), player, file)) { close(talk); return; }
        if (mod.cdv.tnpc.inventory.NpcInventoryTrades.active(talk.npc(), player)) return;
        if (talk.adm().dialogueFile().equals(file)) close(talk);
    }
    @SubscribeEvent public void damaged(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof AutoGunOperator npc)
            npc.getPersistentData().putLong("tnpcLastDamage", npc.level().getGameTime());
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        for (Talk talk : List.copyOf(talks.values())) if (!valid(talk)) close(talk);
    }
    @SubscribeEvent public void open(DialogueOpenEvent event) {
        if (event.getTarget() instanceof AutoGunOperator npc
            && (npc != openingNpc || event.getPlayer() != openingPlayer || !available(npc, event.getPlayer())))
            event.setCanceled(true);
    }
    @SubscribeEvent public void choice(DialogueChoiceEvent event) {
        if (!(event.getTarget() instanceof AutoGunOperator npc)) return;
        Talk talk = talks.get(npc.getUUID());
        if (talk == null || talk.player() != event.getPlayer() || !valid(talk)
            || !talk.adm().dialogueFile().equals(event.getDialogueFile())) {
            event.setCanceled(true);
            if (talk != null && talk.player() == event.getPlayer()) close(talk);
            return;
        }
        if (event.getChoice().id().equals("tnpc_inventory_gift")) {
            // Reject legacy gift choices without touching the player inventory.
            event.setCanceled(true); return;
        }
        if (!event.getChoice().trade().isBlank())
            mod.cdv.tnpc.inventory.NpcInventoryTrades.authorize(npc,event.getPlayer(),event.getChoice().trade());
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        talks.clear();
        mod.cdv.tnpc.inventory.NpcInventoryTrades.clear();
        if (config != null) config.dialogues().keySet().forEach(AdmDialogueApi::unregisterDialogue);
        config = null;
    }
}
