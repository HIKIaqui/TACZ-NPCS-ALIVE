package mod.cdv.tnpc.entity.goal;

import java.util.EnumSet;
import mod.cdv.tnpc.data.NpcBehaviorConfig;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Reposition from an observed incident until safe, timed out, or a combat target is acquired. */
public final class EscapeUnknownDamageGoal extends Goal {
    private final AutoGunOperator npc;
    private Path path;
    private long nextSearch;
    private long revision;

    public EscapeUnknownDamageGoal(AutoGunOperator npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean pending() {
        return npc.isAlive() && npc.getTarget() == null
            && NpcBehaviorConfig.damageEscapeFor(npc.getTemplateId()).enabled()
            && npc.getDamageEscapeMemory().active(npc.level().getGameTime());
    }

    @Override public boolean canUse() {
        if (!pending() || npc.level().getGameTime() < nextSearch) return false;
        path = findPath();
        return path != null;
    }

    @Override public boolean canContinueToUse() { return pending(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void start() {
        if (!pending() || path == null) return;
        revision = npc.getDamageEscapeMemory().revision();
        npc.setSprinting(true);
        npc.getNavigation().moveTo(path, NpcBehaviorConfig.damageEscapeFor(npc.getTemplateId()).speed());
    }

    @Override public void tick() {
        // Mob alternates full selector updates with tickRunningGoals(false).
        // This goal can tick again after clearing its memory, or after the
        // target selector has acquired an enemy, before canContinueToUse runs.
        Vec3 origin = npc.getDamageEscapeMemory().origin();
        if (!pending() || origin == null) return;
        var settings = NpcBehaviorConfig.damageEscapeFor(npc.getTemplateId());
        long now = npc.level().getGameTime();
        if (now < nextSearch) return;
        if (npc.getNavigation().isDone() && safe(npc.blockPosition())
            && npc.position().distanceToSqr(origin) >= settings.minDistance() * settings.minDistance()) {
            npc.getDamageEscapeMemory().clear();
            return;
        }
        if (npc.getNavigation().isDone() || revision != npc.getDamageEscapeMemory().revision()) {
            Path replacement = findPath();
            revision = npc.getDamageEscapeMemory().revision();
            if (replacement != null) npc.getNavigation().moveTo(replacement, settings.speed());
        }
    }

    @Override public void stop() {
        npc.getNavigation().stop();
        npc.setSprinting(false);
        path = null;
    }

    private Path findPath() {
        Vec3 origin = npc.getDamageEscapeMemory().origin();
        if (!pending() || origin == null) return null;
        var settings = NpcBehaviorConfig.damageEscapeFor(npc.getTemplateId());
        nextSearch = npc.level().getGameTime() + settings.repathIntervalTicks();
        double currentDistance = npc.position().distanceToSqr(origin);
        double bestScore = -Double.MAX_VALUE;
        Path best = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            Vec3 candidate = currentDistance < 0.25
                ? LandRandomPos.getPos(npc, settings.searchRadius(), 3)
                : LandRandomPos.getPosAway(npc, settings.searchRadius(), 3, origin);
            if (candidate == null || candidate.distanceToSqr(npc.position()) < 4
                || candidate.distanceToSqr(origin) < Math.max(currentDistance + 1, settings.minDistance() * settings.minDistance())) continue;
            BlockPos destination = BlockPos.containing(candidate);
            if (!safe(destination)) continue;
            Path route = npc.getNavigation().createPath(destination, 0);
            if (route == null || !route.canReach()) continue;
            boolean safeRoute = true;
            // The start may be the campfire that hurt the NPC; it must be allowed to leave it.
            for (int i = 1; i < route.getNodeCount(); i++) {
                var node = route.getNode(i);
                if (!safe(new BlockPos(node.x, node.y, node.z))) { safeRoute = false; break; }
            }
            double score = candidate.distanceToSqr(origin) - 0.2 * candidate.distanceToSqr(npc.position());
            if (safeRoute && score > bestScore) { bestScore = score; best = route; }
        }
        return best;
    }

    private boolean safe(BlockPos feet) {
        for (BlockPos pos : new BlockPos[] {feet.below(), feet, feet.above()}) {
            var state = npc.level().getBlockState(pos);
            if (!state.getFluidState().isEmpty() || CampfireBlock.isLitCampfire(state)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)) return false;
        }
        return true;
    }
}
