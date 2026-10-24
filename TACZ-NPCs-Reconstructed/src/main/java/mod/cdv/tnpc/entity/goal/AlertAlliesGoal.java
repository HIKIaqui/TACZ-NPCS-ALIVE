package mod.cdv.tnpc.entity.goal;

import java.util.EnumSet;
import javax.annotation.Nullable;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;

/** Adopt a recent, valid attacker reported by a nearby ally. */
public class AlertAlliesGoal extends TargetGoal {
    private static final int DAMAGE_MEMORY_TICKS = 100;
    private static final TargetingConditions ATTACKER_CONDITIONS = TargetingConditions.forCombat()
        .ignoreLineOfSight().ignoreInvisibilityTesting();
    private final AutoGunOperator op;
    private final TargetingConditions allyConditions = TargetingConditions.forNonCombat().range(24.0);
    @Nullable private LivingEntity potentialTarget;

    public AlertAlliesGoal(AutoGunOperator op) {
        super(op, false);
        this.op = op;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        // Never carry a candidate over from a previous scan.
        this.potentialTarget = null;
        if (this.op.getTarget() != null || this.op.getTemplate() == null) return false;
        var perception = mod.cdv.tnpc.data.NpcBehaviorConfig.perceptionFor(this.op.getTemplateId());
        double hearingRange = perception.enabled() ? perception.allyAlertRadius() : 24.0;
        if (hearingRange <= 0) return false;
        this.allyConditions.range(hearingRange);
        AABB area = this.op.getBoundingBox().inflate(hearingRange, Math.min(8.0, hearingRange), hearingRange);
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity ally : this.op.level().getNearbyEntities(LivingEntity.class, this.allyConditions, this.op, area)) {
            if (ally == this.op || !this.op.isAlliedTo(ally)) continue;
            LivingEntity attacker = ally.getLastHurtByMob();
            int damageAge = ally.tickCount - ally.getLastHurtByMobTimestamp();
            if (damageAge < 0 || damageAge > DAMAGE_MEMORY_TICKS || attacker == this.op
                || attacker == null || !attacker.isAlive() || this.op.isAlliedTo(attacker)
                || !this.canAttack(attacker, ATTACKER_CONDITIONS)) continue;
            double distance = this.op.distanceToSqr(attacker);
            double followDistance = perception.enabled()
                ? Math.min(this.getFollowDistance(), perception.alertRange()) : this.getFollowDistance();
            if (distance > followDistance * followDistance) continue;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                this.potentialTarget = attacker;
            }
        }
        return this.potentialTarget != null;
    }

    @Override
    public void start() {
        this.targetMob = this.potentialTarget;
        this.op.setTarget(this.potentialTarget);
        super.start();
    }

    @Override
    public void stop() {
        this.potentialTarget = null;
        super.stop();
    }
}
