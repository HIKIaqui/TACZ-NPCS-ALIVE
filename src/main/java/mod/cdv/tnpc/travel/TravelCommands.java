package mod.cdv.tnpc.travel;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class TravelCommands {
    private TravelCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tacznpcs_travel").requires(s -> s.hasPermission(2))
            .then(Commands.literal("list").executes(c -> {
                var entries = TravelLedger.get(c.getSource().getServer()).entries();
                int travelling = (int)entries.stream().filter(e -> e.phase == TravelOwnership.Phase.TRAVELLING).count();
                c.getSource().sendSuccess(() -> Component.literal("Travel records=" + entries.size()
                    + ", travelling=" + travelling + ". Use status <uuid> to inspect an unloaded NPC."), false);
                entries.stream().filter(e -> e.phase == TravelOwnership.Phase.TRAVELLING
                    || e.phase == TravelOwnership.Phase.ARRIVING).limit(20).forEach(e ->
                        c.getSource().sendSuccess(() -> Component.literal(e.uuid + " " + e.phase
                            + " " + e.npc.getString("template")), false));
                return entries.size();
            }))
            .then(Commands.literal("recall").then(Commands.argument("uuid", StringArgumentType.word()).executes(c -> {
                java.util.UUID uuid;
                try { uuid = java.util.UUID.fromString(StringArgumentType.getString(c, "uuid")); }
                catch (IllegalArgumentException bad) { c.getSource().sendFailure(Component.literal("Invalid UUID")); return 0; }
                var ledger = TravelLedger.get(c.getSource().getServer()); var e = ledger.get(uuid);
                if (e == null || e.phase != TravelOwnership.Phase.TRAVELLING) {
                    c.getSource().sendFailure(Component.literal("NPC is not travelling")); return 0;
                }
                e.recall = true; ledger.flush(c.getSource().getServer());
                c.getSource().sendSuccess(() -> Component.literal("Return requested. Origin must be loaded, safe and hidden from nearby players."), true);
                return 1;
            })))
            .then(Commands.literal("status").then(Commands.argument("uuid", StringArgumentType.word()).executes(c -> {
                java.util.UUID uuid;
                try { uuid = java.util.UUID.fromString(StringArgumentType.getString(c, "uuid")); }
                catch (IllegalArgumentException bad) { c.getSource().sendFailure(Component.literal("Invalid UUID")); return 0; }
                var e = TravelLedger.get(c.getSource().getServer()).get(uuid);
                if (e == null) { c.getSource().sendFailure(Component.literal("No travel record for this NPC")); return 0; }
                var q = e.npc.getCompound("tnpcQuests");
                var saved = e.npc.getCompound("tnpcOfflineActions");
                var wallet = new mod.cdv.tnpc.offline.OfflineWallet();
                wallet.restore(saved.getLong("lastTick"), saved.getLong("balanceTicks"), saved.getLong("grantSeen"));
                if (e.phase == TravelOwnership.Phase.TRAVELLING) wallet.observe(c.getSource().getServer().overworld().getGameTime(),
                    mod.cdv.tnpc.offline.OfflineGrants.get(c.getSource().getServer()).total(),
                    mod.cdv.tnpc.offline.OfflineConfig.forLoadout(e.npc.getString("template")).enabled());
                c.getSource().sendSuccess(() -> Component.literal(e.uuid + " phase=" + e.phase + " epoch=" + e.epoch
                    + " dimension=" + e.dimension + " loadout=" + e.npc.getString("template")
                    + " quest=" + q.getCompound("active").getString("id") + " xp=" + q.getLong("xp")
                    + " balanceTicks=" + wallet.balance() + " recall=" + e.recall), false);
                return 1;
            }))));
    }
}
