package mod.cdv.tnpc.companion;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.dialogue.NpcConversations;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
/** Owner-only interception: gun use must not swallow the follower's interaction. */
@EventBusSubscriber(modid="tacznpcs")
public final class CompanionInteraction {
 private static InteractionResult interact(Player player,Entity target){
  if(!(target instanceof AutoGunOperator npc) || player.isShiftKeyDown() || !player.getUUID().equals(Companions.owner(npc)))return InteractionResult.PASS;
  return player.level().isClientSide ? InteractionResult.SUCCESS : player instanceof ServerPlayer p ? NpcConversations.interact(npc,p) : InteractionResult.PASS;
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public static void interact(PlayerInteractEvent.EntityInteract e){
  var result=interact(e.getEntity(),e.getTarget());if(result.consumesAction()){e.setCancellationResult(result);e.setCanceled(true);}
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public static void specific(PlayerInteractEvent.EntityInteractSpecific e){
  var result=interact(e.getEntity(),e.getTarget());if(result.consumesAction()){e.setCancellationResult(result);e.setCanceled(true);}
 }
}
