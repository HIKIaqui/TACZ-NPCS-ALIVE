package mod.cdv.tnpc.quest;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class QuestCommands {
    private QuestCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tacznpcs_quest").requires(source -> source.hasPermission(2))
            .then(Commands.literal("status").then(Commands.argument("npcs", EntityArgument.entities()).executes(c -> {
                int count = 0;
                for (var entity : EntityArgument.getEntities(c, "npcs")) if (entity instanceof AutoGunOperator npc) {
                    var state = npc.getQuestState(); var active = state.active();
                    String message = npc.getUUID() + " [" + npc.getTemplateId() + "] quest=" + (active == null ? "none" : active.id())
                        + ", xp=" + state.xp() + ", level=" + QuestConfig.settings().progression().level(state.xp())
                        + ", health=" + npc.getHealth() + "/" + npc.getMaxHealth() + ", completed=" + state.completedCount()
                        + ", lastCompleted=" + state.lastCompleted()
                        + (active == null ? "" : ", instance=" + active.serial() + ", origin=" + active.dimension() + " " + active.origin().toShortString());
                    c.getSource().sendSuccess(() -> Component.literal(message), false); count++;
                }
                return count;
            })))
            .then(Commands.literal("assign").then(Commands.argument("npcs", EntityArgument.entities())
                .then(Commands.argument("quest", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(QuestConfig.settings().quests().keySet(), b))
                    .executes(c -> {
                        String id = StringArgumentType.getString(c, "quest"); int count = 0;
                        for (var entity : EntityArgument.getEntities(c, "npcs")) if (entity instanceof AutoGunOperator npc
                            && NpcQuests.assign(npc, id, (ServerLevel)npc.level())) count++;
                        int assigned = count;
                        c.getSource().sendSuccess(() -> Component.literal("Assigned " + id + " to " + assigned
                            + " NPC(s). Eligibility rules apply; successful assignment replaces the active quest."), true);
                        return count;
                    }))))
            .then(Commands.literal("xp").then(Commands.argument("npcs", EntityArgument.entities())
                .then(Commands.argument("amount", LongArgumentType.longArg(1, 1000000000L)).executes(c -> {
                    long amount = LongArgumentType.getLong(c, "amount"); int count = 0;
                    for (var entity : EntityArgument.getEntities(c, "npcs")) if (entity instanceof AutoGunOperator npc && npc.isAlive()) {
                        NpcQuests.addXp(npc, amount); count++;
                    }
                    int changed = count;
                    c.getSource().sendSuccess(() -> Component.literal("Added " + amount + " XP to " + changed + " NPC(s)."), true);
                    return count;
                }))))
            .then(Commands.literal("clear").then(Commands.argument("npcs", EntityArgument.entities()).executes(c -> {
                int count = 0;
                for (var entity : EntityArgument.getEntities(c, "npcs")) if (entity instanceof AutoGunOperator npc) {
                    npc.getQuestState().clear(c.getSource().getServer().overworld().getGameTime() + QuestConfig.settings().assignmentRetryTicks()); count++;
                }
                int changed = count;
                c.getSource().sendSuccess(() -> Component.literal("Cleared quests for " + changed + " NPC(s); XP preserved."), true);
                return count;
            })))
            .then(Commands.literal("check").then(Commands.argument("npcs", EntityArgument.entities()).executes(c -> {
                int completed = 0;
                for (var entity : EntityArgument.getEntities(c, "npcs")) if (entity instanceof AutoGunOperator npc
                    && NpcQuests.check(npc, (ServerLevel)npc.level())) completed++;
                int result = completed;
                c.getSource().sendSuccess(() -> Component.literal("Quest check: " + result + " completed."), false);
                return completed;
            })))
            .then(Commands.literal("dialogue").then(Commands.argument("npcs", EntityArgument.entities()).executes(c -> {
                int count = 0;
                for (var entity : EntityArgument.getEntities(c, "npcs")) if (entity instanceof AutoGunOperator npc) {
                    var lines = NpcQuests.dialogueLines(npc);
                    c.getSource().sendSuccess(() -> Component.literal("[" + npc.getTemplateId() + "] activity=" + lines.activity() + "; news=" + lines.news()), false); count++;
                }
                return count;
            }))));
    }
}
