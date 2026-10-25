package mod.cdv.tnpc.entity.goal;

import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

/** Preserves the original nearby-enemy alert without owning or clearing our target. */
public class AlertEnemiesGoal extends Goal {
    private final AutoGunOperator op;
    private final TargetingConditions conditions = TargetingConditions.forCombat().range(24.0);
    private long nextScanTick;

    public AlertEnemiesGoal(AutoGunOperator op) {
        this.op = op;
        // No MOVE, LOOK or TARGET locks: this is a throttled notification task.
    }

    @Override
    public boolean canUse() {
        return this.op.getTemplate() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void tick() {
        long now = this.op.level().getGameTime();
        if (now < this.nextScanTick) return;
        this.nextScanTick = now + 20;
        // This notification must not bypass our reduced perception while calm/resting.
        double scanRange = Math.min(24.0, this.op.getDetectionRange());
        this.conditions.range(scanRange);
        for (PathfinderMob enemy : this.op.level().getNearbyEntities(PathfinderMob.class,
                this.conditions, this.op, this.op.getBoundingBox().inflate(scanRange, Math.min(8.0, scanRange), scanRange))) {
            // We only know the recipient's relationship policy for our own NPCs.
            // Vanilla/modded mobs retain their own target selection instead of being forced to retaliate.
            if (!(enemy instanceof AutoGunOperator recipient)) continue;
            double range = Math.min(enemy.getAttributeValue(Attributes.FOLLOW_RANGE), recipient.getDetectionRange());
            if (enemy.getTarget() == null && this.op.distanceToSqr(enemy) < range * range
                && recipient.isOpposedTo(this.op)
                && enemy.canAttack(this.op)) {
                enemy.setTarget(this.op);
            }
        }
    }
}
