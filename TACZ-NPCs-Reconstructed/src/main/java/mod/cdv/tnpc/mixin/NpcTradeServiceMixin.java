package mod.cdv.tnpc.mixin;

import java.util.UUID;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.inventory.NpcInventoryTrades;
import net.aviel.dialogue.npc.NpcTradeService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=NpcTradeService.class, remap=false)
public abstract class NpcTradeServiceMixin {
    @Inject(method="openTrade",at=@At("HEAD"),cancellable=true,remap=false)
    private static void tnpc$open(ServerPlayer player, Entity target, String file, CallbackInfo ci) {
        if (target instanceof AutoGunOperator npc) { NpcInventoryTrades.open(player,npc,file); ci.cancel(); }
    }
    @Inject(method="executeTrade",at=@At("HEAD"),cancellable=true,remap=false)
    private static void tnpc$trade(ServerPlayer player, UUID uuid, String file, String id, int index, int amount, CallbackInfo ci) {
        if (player != null && uuid != null && player.serverLevel().getEntity(uuid) instanceof AutoGunOperator npc) {
            NpcInventoryTrades.execute(player,npc,file,id,index,amount); ci.cancel();
        }
    }
}
