// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.entity.goal;

import com.tacz.guns.item.ModernKineticGunItem;
import java.util.EnumSet;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;

public class GunMeleeGoal<T extends AutoGunOperator>
extends Goal {
    private final T mob;
    private final int minDistance;
    private int cooldown = 0;

    public GunMeleeGoal(T mob, int minDistance) {
        this.mob = mob;
        this.minDistance = minDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    public boolean canUse() {
        return this.mob.getTarget() != null && this.isHoldingGun() && this.mob.distanceToSqr((Entity)this.mob.getTarget()) <= (double)this.minDistance;
    }

    private boolean isHoldingGun() {
        return this.mob.isHolding(is -> is.getItem() instanceof ModernKineticGunItem);
    }

    public void tick() {
        this.mob.getLookControl().setLookAt((Entity)this.mob.getTarget(), 30.0f, 30.0f);
        if (this.mob.getSensing().hasLineOfSight((Entity)this.mob.getTarget())) {
            if (this.cooldown == 0) {
                ((AutoGunOperator)(this.mob)).melee();
                this.cooldown = this.mob.getRandom().nextInt(80, 200);
            }
            --this.cooldown;
        }
    }
}

