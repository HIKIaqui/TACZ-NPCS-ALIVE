package mod.cdv.tnpc.client;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
/** TaCZ's NORMAL-priority handler cancels gun clicks before entity interaction can run. */
@EventBusSubscriber(modid="tacznpcs",value=Dist.CLIENT)
public final class CompanionInput {
 @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
 public static void use(InputEvent.InteractionKeyMappingTriggered event){
  var mc=Minecraft.getInstance();var p=mc.player;
  if(!event.isUseItem() || p==null || p.isShiftKeyDown() || mc.screen!=null
    || !(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof AutoGunOperator npc)
    || !p.getUUID().equals(npc.syncedCompanionOwner()))return;
  event.setCanceled(false);event.setSwingHand(false);
 }
}
