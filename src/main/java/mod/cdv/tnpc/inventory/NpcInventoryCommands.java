package mod.cdv.tnpc.inventory;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class NpcInventoryCommands {
    public static void register(RegisterCommandsEvent event) {
        var build = event.getBuildContext();
        event.getDispatcher().register(Commands.literal("tacznpcs_inventory").requires(s -> s.hasPermission(2))
            .then(Commands.literal("give").then(Commands.argument("npcs", EntityArgument.entities())
                .then(Commands.argument("item", ItemArgument.item(build))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64000)).executes(c -> {
                    var input = ItemArgument.getItem(c, "item"); int count = IntegerArgumentType.getInteger(c, "count"), affected = 0;
                    for (var entity : EntityArgument.getEntities(c, "npcs")) if (entity instanceof AutoGunOperator npc && npc.isAlive()) {
                        int accepted=npc.getNpcInventory().insert(input.createItemStack(count, false));
                        if(accepted>0) affected++;
                        c.getSource().sendSuccess(() -> Component.literal(npc.getUUID()+": accepted "+accepted+"/"+count+" items"),false);
                    }
                    int total = affected; c.getSource().sendSuccess(() -> Component.literal("Added inventory items to " + total + " NPC(s)."), true);
                    return affected;
                })))))
            .then(Commands.literal("list").then(Commands.argument("npc", EntityArgument.entity()).executes(c -> {
                if (!(EntityArgument.getEntity(c,"npc") instanceof AutoGunOperator npc)) return 0;
                var items = npc.getNpcInventory().allItems();
                c.getSource().sendSuccess(() -> Component.literal(npc.getUUID() + ": " + npc.getNpcInventory().cargoSnapshot().size()+"/"+NpcInventoryConfig.forLoadout(npc.getTemplateId()).maxCargoSlots()+" cargo slots, "+items.size()+" stacks including equipment"), false);
                var policy=NpcInventoryConfig.forLoadout(npc.getTemplateId());
                for (var stack : items.stream().limit(40).toList())
                    c.getSource().sendSuccess(() -> Component.literal(stack.getCount() + "x ").append(stack.getHoverName())
                        .append(Component.literal(" | interest="+policy.interest(stack)+", basePrice="
                            +(policy.basePrice(stack).isPresent() ? policy.basePrice(stack).getAsInt() : "unset")+", protected="+policy.protectedItem(stack))), false);
                return items.size();
            })))
            .then(Commands.literal("collect").then(Commands.argument("npc", EntityArgument.entity())
                .then(Commands.argument("item", EntityArgument.entity()).executes(c -> {
                    if (EntityArgument.getEntity(c,"npc") instanceof AutoGunOperator npc
                        && EntityArgument.getEntity(c,"item") instanceof ItemEntity item && NpcItemActions.collect(npc,item)) return 1;
                    c.getSource().sendFailure(Component.literal("The NPC cannot collect that item here.")); return 0;
                })))));
    }
}
