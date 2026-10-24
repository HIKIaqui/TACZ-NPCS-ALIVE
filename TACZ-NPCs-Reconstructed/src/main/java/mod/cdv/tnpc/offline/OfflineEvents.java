package mod.cdv.tnpc.offline;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class OfflineEvents {
    private static MinecraftServer budgetServer;
    private static long budgetTick = Long.MIN_VALUE;
    private static int attempts;
    public static boolean claimAttempt(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (server != budgetServer || now != budgetTick) { budgetServer = server; budgetTick = now; attempts = 0; }
        if (attempts >= OfflineConfig.settings().maxActionsPerTick()) return false;
        attempts++; return true;
    }
    @SubscribeEvent public void joined(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof AutoGunOperator npc)
            npc.getOfflineActions().observe(npc, level);
    }
    @SubscribeEvent public void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        budgetServer = null; budgetTick = Long.MIN_VALUE; attempts = 0;
    }
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tacznpcs_offline").requires(source -> source.hasPermission(2))
            .then(Commands.literal("add").then(Commands.argument("points", IntegerArgumentType.integer(1, 1000000))
                .executes(context -> {
                    int points = IntegerArgumentType.getInteger(context, "points");
                    long ticks = (long) points * OfflineConfig.settings().ticksPerPoint();
                    try { OfflineGrants.get(context.getSource().getServer()).add(ticks); }
                    catch (ArithmeticException overflow) {
                        context.getSource().sendFailure(Component.literal("Global offline credit limit reached.")); return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal("Added " + points
                        + " offline points to all existing NPCs. Unloaded NPCs receive them when loaded."), true);
                    return 1;
                })))
            .then(Commands.literal("status").then(Commands.argument("npcs", EntityArgument.entities())
                .executes(context -> {
                    int count = 0;
                    for (var entity : EntityArgument.getEntities(context, "npcs")) {
                        if (!(entity instanceof AutoGunOperator npc)) continue;
                        npc.getOfflineActions().observe(npc, (ServerLevel)npc.level());
                        var state = npc.getOfflineActions(); var wallet = state.wallet();
                        String message = npc.getUUID() + " [" + npc.getTemplateId() + "] balance="
                            + wallet.balance() + " ticks (" + String.format(java.util.Locale.ROOT, "%.2f",
                            (double)wallet.balance() / OfflineConfig.settings().ticksPerPoint())
                            + " points), lastTick=" + wallet.lastTick() + ", lastAction=" + state.lastAction();
                        context.getSource().sendSuccess(() -> Component.literal(message), false); count++;
                    }
                    return count;
                }))));
    }
}
