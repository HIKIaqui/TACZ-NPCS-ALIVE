package mod.cdv.tnpc.entity.goal;

import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/** Awareness limits acquisition; vanilla FOLLOW_RANGE and unseen memory still govern pursuit. */
public final class PerceptionTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private final AutoGunOperator npc;
    public PerceptionTargetGoal(AutoGunOperator npc) {
        super(npc, LivingEntity.class, true, target -> npc.getTemplate() != null && mod.cdv.tnpc.companion.CompanionCombat.acquires(npc,target));
        this.npc = npc;
    }
    @Override public boolean canContinueToUse() {
        return npc.getTarget() != null && mod.cdv.tnpc.companion.CompanionCombat.acquires(npc,npc.getTarget()) && super.canContinueToUse();
    }
    @Override protected void findTarget() {
        double range = npc.getDetectionRange();
        targetConditions.range(range);
        target = npc.level().getNearestEntity(
            npc.level().getEntitiesOfClass(LivingEntity.class, getTargetSearchArea(range), entity -> true),
            targetConditions, npc, npc.getX(), npc.getEyeY(), npc.getZ());
    }
}
