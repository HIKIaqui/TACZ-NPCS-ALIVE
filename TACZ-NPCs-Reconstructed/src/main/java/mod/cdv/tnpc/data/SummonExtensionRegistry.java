// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.data;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import mod.cdv.tnpc.data.TemplateDataManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber
public class SummonExtensionRegistry {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandNode<CommandSourceStack> summonNode = dispatcher.getRoot().getChild("summon");
        if (summonNode == null) {
            return;
        }
        CommandNode<CommandSourceStack> entityNode = summonNode.getChild("entity");
        if (entityNode == null) {
            return;
        }
        CommandNode<CommandSourceStack> posNode = entityNode.getChild("pos");
        if (posNode == null) {
            return;
        }
        CommandNode<CommandSourceStack> oldNbtNode = posNode.getChild("nbt");
        if (oldNbtNode == null) {
            return;
        }
        posNode.getChildren().remove(oldNbtNode);
        posNode.getChildren().removeIf(node -> node.getName().equals("nbt"));
        RequiredArgumentBuilder<CommandSourceStack, CompoundTag> newNbtBuilder = Commands.argument("nbt", CompoundTagArgument.compoundTag()).suggests((context, builder) -> {
            String input = context.getInput();
            if (input.contains("tacznpcs:npc")) {
                return SharedSuggestionProvider.suggest(TemplateDataManager.TEMPLATES.keySet().stream().map(name -> "{template:\"" + name + "\"}"), (SuggestionsBuilder)builder);
            }
            return builder.buildFuture();
        }).executes(oldNbtNode.getCommand());
        if (oldNbtNode.getRedirect() != null) {
            newNbtBuilder.forward(oldNbtNode.getRedirect(), oldNbtNode.getRedirectModifier(), oldNbtNode.isFork());
        }
        posNode.addChild((CommandNode)newNbtBuilder.build());
    }
}

