// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.entity.goal;

import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.item.ModernKineticGunItem;
import java.util.EnumSet;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

public class FireAtNearestTargetGoal<T extends AutoGunOperator>
extends Goal {
    public static final UniformInt PATHFINDING_DELAY_RANGE = TimeUtil.rangeOfSeconds((int)1, (int)2);
    private final T mob;
    private final mod.cdv.tnpc.entity.ai.CombatMemory combat = new mod.cdv.tnpc.entity.ai.CombatMemory();
    public mod.cdv.tnpc.entity.ai.CombatMemory combatMemory() { return combat; }
    public void targetChanged(LivingEntity target) {
        combat.acquired(target == null ? null : target.getUUID(), mob.level().getGameTime(),
            mod.cdv.tnpc.data.NpcBehaviorConfig.combatFor(mob.getTemplateId()), mob.getRandom()::nextInt);
        attackBurst = 0;
        if (gunState == GunState.READY_TO_ATTACK) { gunState = GunState.LOADED; attackDelay = 0; }
    }
    public void observeTarget() {
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive()) combat.observe(target.getUUID(), mob.getSensing().hasLineOfSight(target),
            mob.level().getGameTime(), mod.cdv.tnpc.data.NpcBehaviorConfig.combatFor(mob.getTemplateId()), mob.getRandom()::nextInt);
    }
    public GunState gunState;
    public ShootResult lastResult = ShootResult.NOT_GUN;
    public final double speedModifier;
    public final float attackRadiusSqr;
    public int seeTime;
    public int attackDelay;
    public int attackBurst;
    public int updatePathDelay;
    public Path panicPath;
    private boolean strafingBackwards;
    private boolean strafingClockwise;
    private int strafingTime;

    public FireAtNearestTargetGoal(T p_25814_, double p_25815_, float p_25816_) {
        this.gunState = GunState.EMPTY;
        this.mob = p_25814_;
        this.speedModifier = p_25815_;
        this.attackRadiusSqr = p_25816_ * p_25816_;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    public boolean canUse() {
        return this.isValidTarget() && this.isHoldingGun();
    }

    public boolean isHoldingGun() {
        return this.mob.getMainHandItem().getItem() instanceof ModernKineticGunItem;
    }

    public boolean canContinueToUse() {
        return this.isValidTarget() && (this.canUse() || !this.mob.getNavigation().isDone()) && this.isHoldingGun();
    }

    private boolean isValidTarget() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && !target.isSpectator()
            && this.mob.canAttack(target) && !this.mob.isAlliedTo(target);
    }

    @Override
    public void start() {
        this.mob.setAggressive(true);
        this.updatePathDelay = 0;
    }

    public void stop() {
        super.stop();
        this.mob.setAggressive(false);
        // Stopping movement must not erase the target selected by targetSelector.
        this.mob.getNavigation().stop();
        this.seeTime = 0;
        this.updatePathDelay = 0;
        this.lastResult = ShootResult.NOT_GUN;
        ((AutoGunOperator)(this.mob)).aim(false);
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        LivingEntity livingentity = this.mob.getTarget();
        if (this.isValidTarget()) {
            boolean flag2;
            double d0;
            boolean flag1;
            this.panicPath = null;
            boolean flag = this.mob.getSensing().hasLineOfSight((Entity)livingentity);
            var profile = mod.cdv.tnpc.data.NpcBehaviorConfig.combatFor(mob.getTemplateId());
            var weapon = profile.weapon(mob.combatGunType());
            long now = mob.level().getGameTime();
            combat.observe(livingentity.getUUID(), flag, now, profile, mob.getRandom()::nextInt);
            boolean bl = flag1 = this.seeTime > 0;
            if (flag != flag1) {
                this.seeTime = 0;
            }
            this.seeTime = flag ? ++this.seeTime : --this.seeTime;
            ++this.strafingTime;
            if (this.strafingTime >= 80) {
                if ((double)this.mob.getRandom().nextFloat() < 0.3) {
                    boolean bl2 = this.strafingClockwise = !this.strafingClockwise;
                }
                if ((double)this.mob.getRandom().nextFloat() < 0.3) {
                    this.strafingBackwards = !this.strafingBackwards;
                }
                this.strafingTime = 0;
            }
            if ((d0 = this.mob.distanceToSqr((Entity)livingentity)) > (double)(this.attackRadiusSqr * 0.75f)) {
                this.strafingBackwards = false;
            } else if (d0 < (double)(this.attackRadiusSqr * 0.25f)) {
                this.strafingBackwards = true;
            }
            boolean bl3 = flag2 = (d0 > (double)this.attackRadiusSqr || this.seeTime < 5) && this.attackDelay <= 0;
            if (flag2) {
                --this.updatePathDelay;
                if (this.updatePathDelay <= 0) {
                    this.mob.getNavigation().moveTo((Entity)livingentity, this.canRun() ? this.speedModifier : this.speedModifier * (double)0.8f);
                    this.updatePathDelay = PATHFINDING_DELAY_RANGE.sample(this.mob.getRandom());
                }
            } else {
                this.updatePathDelay = 0;
                this.mob.getNavigation().stop();
            }
            if (this.mob.isWithinMeleeAttackRange(livingentity) && this.mob.hasMelee()) {
                return;
            }
            this.mob.getLookControl().setLookAt((Entity)livingentity, 30.0f, 30.0f);
            this.mob.aim(flag && d0 >= profile.aimDistance() * profile.aimDistance());
            if (this.gunState == GunState.EMPTY) {
                ((AutoGunOperator)(this.mob)).reload();
                this.gunState = GunState.RELOADING;
            } else if (this.gunState == GunState.RELOADING) {
                if (((AutoGunOperator)(this.mob)).getDataHolder().reloadStateType == ReloadState.StateType.TACTICAL_RELOAD_FINISHING || ((AutoGunOperator)(this.mob)).getDataHolder().reloadStateType == ReloadState.StateType.EMPTY_RELOAD_FINISHING || ((AutoGunOperator)(this.mob)).getDataHolder().reloadStateType == ReloadState.StateType.NOT_RELOADING) {
                    this.gunState = GunState.LOADED;
                    this.attackDelay = 0;
                }
            } else if (this.gunState == GunState.LOADED) {
                --this.attackDelay;
                if (this.attackDelay <= 0) {
                    this.gunState = GunState.READY_TO_ATTACK;
                    this.attackBurst = ((AutoGunOperator)(this.mob)).getStateBurst();
                }
            } else if (this.gunState == GunState.READY_TO_ATTACK && combat.mayFire(livingentity.getUUID(), flag, now)) {
                var error = mod.cdv.tnpc.entity.ai.CombatAim.offset(profile, Math.sqrt(d0), mob.getRandom().nextDouble(), mob.getRandom().nextDouble(),
                    mod.cdv.tnpc.quest.QuestConfig.settings().progression().aimError().multiplier(
                        mod.cdv.tnpc.quest.NpcQuests.level(mob)));
                float pitch = Math.max(-89.9f, Math.min(89.9f, mob.getViewXRot(1.0f) + error.pitch()));
                float yaw = mob.getViewYRot(1.0f) + error.yaw();
                // Stable suppliers: repeated reads/pellets share the same NPC aim centre.
                this.lastResult = mob.shoot(() -> pitch, () -> yaw);
                if (this.lastResult == ShootResult.NOT_DRAW) {
                    ((AutoGunOperator)(this.mob)).draw(() -> this.mob.getMainHandItem());
                    return;
                }
                if (this.lastResult == ShootResult.NO_AMMO) {
                    this.gunState = GunState.EMPTY;
                    return;
                }
                if (this.lastResult == ShootResult.NEED_BOLT) {
                    ((AutoGunOperator)(this.mob)).bolt();
                    return;
                }
                if (this.lastResult == ShootResult.SUCCESS) {
                    combat.fired(now, weapon.shotIntervalTicks().sample(mob.getRandom()::nextInt));
                    --this.attackBurst;
                    if (this.attackBurst <= 0) {
                        this.attackDelay();
                        this.gunState = GunState.LOADED;
                    }
                }
            }
        }
    }

    public void attackDelay() {
        this.attackDelay = mob.getStateRangedCooldown();
    }

    private boolean canRun() {
        return this.gunState == GunState.EMPTY;
    }

    public static enum GunState {
        EMPTY,
        RELOADING,
        LOADED,
        READY_TO_ATTACK;

    }
}

