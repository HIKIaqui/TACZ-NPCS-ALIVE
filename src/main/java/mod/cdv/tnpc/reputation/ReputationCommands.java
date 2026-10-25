package mod.cdv.tnpc.reputation;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mod.cdv.tnpc.data.TemplateDataManager;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ReputationCommands {
    private ReputationCommands() {}
    public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("tacznpcs_reputation").requires(source -> source.hasPermission(2));
        root.then(Commands.literal("status").then(Commands.argument("player",GameProfileArgument.gameProfile())
            .then(Commands.argument("npc",EntityArgument.entity()).executes(c -> {
                var entity = EntityArgument.getEntity(c,"npc");
                if (!(entity instanceof AutoGunOperator npc)) { c.getSource().sendFailure(Component.literal("Target must be a TACZ NPC.")); return 0; }
                var data = ReputationData.get(c.getSource().getServer());
                for (GameProfile p : GameProfileArgument.getGameProfiles(c,"player")) {
                    long effective = data.effective(p.getId(),npc.getFactionId(),npc.getUUID());
                    c.getSource().sendSuccess(() -> Component.literal("player="+p.getId()+", npc="+npc.getUUID()+", faction="+npc.getFactionId()
                        +", factionReputation="+data.faction(p.getId(),npc.getFactionId())+", personal="+data.personal(p.getId(),npc.getUUID())
                        +", effective="+effective+", tier="+ReputationConfig.tier(effective)),false);
                }
                return 1;
            }))));
        var faction = Commands.argument("faction",StringArgumentType.string()).suggests((c,b) -> SharedSuggestionProvider.suggest(
            TemplateDataManager.TEMPLATES.values().stream().map(t -> t.faction()).distinct(),b));
        var personal = Commands.argument("npc_uuid",UuidArgument.uuid());
        for (String action : new String[]{"get","set","add","reset"}) {
            faction.then(operation(action,false)); personal.then(operation(action,true));
        }
        root.then(Commands.literal("faction").then(Commands.argument("player",GameProfileArgument.gameProfile()).then(faction)));
        root.then(Commands.literal("personal").then(Commands.argument("player",GameProfileArgument.gameProfile()).then(personal)));
        event.getDispatcher().register(root);
    }
    private static LiteralArgumentBuilder<CommandSourceStack> operation(String action,boolean personal) {
        var builder = Commands.literal(action);
        if (action.equals("set") || action.equals("add")) builder.then(Commands.argument("value",IntegerArgumentType.integer(-ReputationConfig.LIMIT,ReputationConfig.LIMIT))
            .executes(c -> update(c,personal,action)));
        else builder.executes(c -> update(c,personal,action));
        return builder;
    }
    private static int update(CommandContext<CommandSourceStack> c,boolean personal,String action) throws CommandSyntaxException {
        var data = ReputationData.get(c.getSource().getServer());
        String faction = personal ? "" : StringArgumentType.getString(c,"faction");
        try { if (!personal) ReputationConfig.id(faction); }
        catch (IllegalArgumentException invalid) { c.getSource().sendFailure(Component.literal(invalid.getMessage())); return 0; }
        var npc = personal ? UuidArgument.getUuid(c,"npc_uuid") : null;
        int count = 0;
        for (GameProfile player : GameProfileArgument.getGameProfiles(c,"player")) {
            int before = personal ? data.personal(player.getId(),npc) : data.faction(player.getId(),faction);
            if (action.equals("reset")) {
                if (personal) data.personal(player.getId(),npc,0); else data.resetFaction(player.getId(),faction);
            } else if (!action.equals("get")) {
                int value = IntegerArgumentType.getInteger(c,"value");
                if (action.equals("add")) value = ReputationData.add(before,value);
                if (personal) data.personal(player.getId(),npc,value); else data.faction(player.getId(),faction,value);
            }
            int after = personal ? data.personal(player.getId(),npc) : data.faction(player.getId(),faction);
            c.getSource().sendSuccess(() -> Component.literal(player.getId()+" "+(personal ? "npc="+npc : "faction="+faction)+": "+before+" -> "+after),false); count++;
        }
        return count;
    }
}
