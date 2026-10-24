// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.entity.goal;

import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

public class SeekShelterFromTargetGoal<T extends PathfinderMob>
extends Goal {
    int maxDistance;
    final Predicate<T> hidePredicate;
    T mob;

    public SeekShelterFromTargetGoal(T mob, int maxDistance, Predicate<T> hidePredicate) {
        this.maxDistance = maxDistance;
        this.hidePredicate = hidePredicate;
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    public boolean canUse() {
        return this.mob.getTarget() != null && this.hidePredicate.test(this.mob);
    }

    public void tick() {
        if (this.mob.getNavigation().isInProgress()) {
            return;
        }
        LivingEntity target = this.mob.getTarget();
        Level level = this.mob.level();
        @Nullable Vec3 randPos = LandRandomPos.getPosAway(this.mob, (int)this.maxDistance, (int)7, (Vec3)target.getPosition(1.0f));
        if (randPos != null) {
            BlockPos random = BlockPos.containing((Position)randPos);
            BlockHitResult hit = level.clip(new ClipContext(target.getEyePosition(), random.above(3).getBottomCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) {
                this.mob.getNavigation().moveTo(randPos.x, randPos.y, randPos.z, (double)1.2f);
                System.out.println("Found: " + String.valueOf(randPos));
            }
        }
    }

    private static boolean isValidSpace(Level level, BlockPos pos) {
        BlockState floor = level.getBlockState(pos.below());
        BlockState body = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        return floor.isCollisionShapeFullBlock((BlockGetter)level, pos.below()) && body.isAir() && head.isAir();
    }
}

