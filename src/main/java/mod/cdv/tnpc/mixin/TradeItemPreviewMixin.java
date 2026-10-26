package mod.cdv.tnpc.mixin;

import mod.cdv.tnpc.client.TradeItemPreviews;
import net.aviel.dialogue.npc.trade.NpcTradeDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Minecraft;
import mod.cdv.tnpc.client.TradeScreenAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="net.aviel.dialogue.client.NpcTradeItems",remap=false)
public abstract class TradeItemPreviewMixin {
    @Inject(method="countInventory",at=@At("HEAD"),cancellable=true,remap=false)
    private static void tnpc$paymentInventory(LocalPlayer player,Item item,CallbackInfoReturnable<Integer> cir) {
        if(Minecraft.getInstance().screen instanceof TradeScreenAccessor screen && screen.tnpc$tradeFile().startsWith("tnpc_stock_")) {
            int count=0;
            if(player!=null && item!=null) for(var stack:player.getInventory().items) if(stack.is(item)) count+=stack.getCount();
            cir.setReturnValue(count);
        }
    }
    @Inject(method="stackFor",at=@At("HEAD"),cancellable=true,remap=false)
    private static void tnpc$preview(NpcTradeDefinition.TradeItem item,CallbackInfoReturnable<ItemStack> cir) {
        var stack=TradeItemPreviews.get(item); if(stack!=null) cir.setReturnValue(stack);
    }
}
