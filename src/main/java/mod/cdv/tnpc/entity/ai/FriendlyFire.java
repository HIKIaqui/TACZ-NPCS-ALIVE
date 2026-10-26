package mod.cdv.tnpc.entity.ai;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.companion.Companions;
import mod.cdv.tnpc.data.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
@EventBusSubscriber(modid="tacznpcs")
public final class FriendlyFire {
 private static final TagKey<DamageType> BULLETS=TagKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("tacz","bullets"));
 private FriendlyFire(){}
 public static boolean sameGroup(AutoGunOperator shooter,LivingEntity victim) {
  var owner=Companions.owner(shooter);
  return owner!=null && (owner.equals(victim.getUUID()) || (victim instanceof AutoGunOperator npc && owner.equals(Companions.owner(npc))));
 }
 public static boolean allied(AutoGunOperator shooter,LivingEntity victim) {
  return shooter!=victim && (sameGroup(shooter,victim) || shooter.isAlliedTo(victim));
 }
 /** Broad phase uses the local entity index; exact phase clips inflated ally bodies against both aim segments. */
 public static boolean blocked(AutoGunOperator shooter,LivingEntity target,float pitch,float yaw,FriendlyFireSettings settings) {
  if(!settings.checkEnabled())return false;
  Vec3 from=shooter.getEyePosition(),intended=target.getEyePosition();
  Vec3 actual=from.add(Vec3.directionFromRotation(pitch,yaw).scale(from.distanceTo(intended)));
  double margin=settings.corridorRadius();
  AABB area=new AABB(from,intended).minmax(new AABB(from,actual)).inflate(margin);
  for(var ally:shooter.level().getEntitiesOfClass(LivingEntity.class,area,
      e->e!=target && e!=shooter && e.isAlive() && !e.isSpectator() && e.isPickable())) {
   if(allied(shooter,ally) && (intersects(ally.getBoundingBox(),from,intended,margin) || intersects(ally.getBoundingBox(),from,actual,margin)))return true;
  }
  return false;
 }
 public static boolean intersects(AABB body,Vec3 from,Vec3 to,double margin) {
  var box=body.inflate(margin);return box.contains(from) || box.clip(from,to).isPresent();
 }
 @SubscribeEvent public static void damage(LivingIncomingDamageEvent event) {
  if(event.getEntity().level().isClientSide || event.getAmount()<=0
      || !(event.getSource().getEntity() instanceof AutoGunOperator shooter)
      || !(event.getSource().is(BULLETS) || event.getSource().getDirectEntity() instanceof com.tacz.guns.entity.EntityKineticBullet))return;
  var settings=NpcBehaviorConfig.combatFor(shooter.getTemplateId()).friendlyFire();
  if(!settings.damageEnabled() || !allied(shooter,event.getEntity()))return;
  boolean nearby=shooter.distanceToSqr(event.getEntity())<=settings.nearbyBlocks()*settings.nearbyBlocks();
  event.setAmount((float)(event.getAmount()*settings.damageMultiplier(nearby,sameGroup(shooter,event.getEntity()))));
 }
}
