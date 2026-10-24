package mod.cdv.tnpc.campfire;

import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.data.NpcBehaviorConfig;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;

public final class NpcCampfires {
    private NpcCampfires() {}
    public static RestReservations.Site site(ServerLevel level, BlockPos pos, boolean campfire) {
        return new RestReservations.Site(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(), campfire);
    }
    public static RestReservations.Spot spot(BlockPos pos) { return new RestReservations.Spot(pos.getX(), pos.getY(), pos.getZ()); }
    public static boolean isUsable(BlockState state, boolean canLight) {
        return state.is(Blocks.CAMPFIRE) && !state.getValue(CampfireBlock.WATERLOGGED)
            && (state.getValue(CampfireBlock.LIT) || canLight);
    }
    public static boolean canReachFire(AutoGunOperator npc, BlockPos pos) {
        if (npc.distanceToSqr(Vec3.atCenterOf(pos)) > 25) return false;
        BlockHitResult hit = npc.level().clip(new ClipContext(npc.getEyePosition(), Vec3.atCenterOf(pos),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
    public static boolean isReplaceableSpace(BlockState state) {
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }
    public static boolean spaceForNew(ServerLevel level, AutoGunOperator npc, BlockPos pos) {
        var settings = NpcBehaviorConfig.campfireFor(npc.getTemplateId());
        if (!level.getWorldBorder().isWithinBounds(pos) || pos.getY() <= level.getMinBuildHeight()
            || pos.getY() + 2 >= level.getMaxBuildHeight() || !level.hasChunkAt(pos)
            || (settings.requireSky() && !level.canSeeSky(pos))) return false;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos floor = pos.offset(x, -1, z);
            if (!level.hasChunkAt(floor) || !level.hasChunkAt(floor.above(2))
                || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                || level.getBlockState(floor).getBlock() instanceof CampfireBlock
                || !isReplaceableSpace(level.getBlockState(floor.above())) || !isReplaceableSpace(level.getBlockState(floor.above(2)))
                || !level.getFluidState(floor).isEmpty()) return false;
        }
        return level.getEntities(npc, new AABB(pos).inflate(0.1)).isEmpty();
    }
    public static boolean fireNearby(ServerLevel level, AutoGunOperator npc, BlockPos pos) {
        int spacing = NpcBehaviorConfig.campfires().minSpacing();
        if (CampfireTimers.get(level).hasPendingScanNear(pos, spacing)) return true;
        if (CampfireEvents.RESERVATIONS.campfireNear(site(level, pos, true), npc.getUUID(), spacing, level.getGameTime())) return true;
        return level.getPoiManager().getInRange(type -> type.is(CampfirePois.CAMPFIRE.getKey()), pos, spacing, PoiManager.Occupancy.ANY)
            .anyMatch(poi -> poi.getPos().distSqr(pos) < (long)spacing * spacing);
    }
    public static boolean build(ServerLevel level, AutoGunOperator npc, BlockPos pos) {
        var settings = NpcBehaviorConfig.campfireFor(npc.getTemplateId());
        if (!settings.useAsRestPoi() || !settings.canCreate() || !EventHooks.canEntityGrief(level, npc)
            || !canReachFire(npc, pos) || !spaceForNew(level, npc, pos) || fireNearby(level, npc, pos)) return false;
        BlockState old = level.getBlockState(pos);
        BlockState placed = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
        // Defer neighbor/shape updates until the placement event accepts it. Otherwise
        // replacing the lower half of tall vegetation could destroy the upper half
        // even when another mod cancels the placement.
        int stagedFlags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        if (!level.setBlock(pos, placed, stagedFlags)) return false;
        // For non-player entities NeoForge reads the snapshot's state as the placed block.
        if (EventHooks.onBlockPlace(npc, BlockSnapshot.create(level.dimension(), level, pos), Direction.UP)) {
            level.setBlock(pos, old, stagedFlags); return false;
        }
        level.updateNeighborsAt(pos, old.getBlock());
        old.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_ALL);
        placed.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        placed.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_ALL);
        npc.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1, 1);
        return level.getBlockState(pos).is(Blocks.CAMPFIRE);
    }
    public static boolean light(ServerLevel level, AutoGunOperator npc, BlockPos pos) {
        var settings = NpcBehaviorConfig.campfireFor(npc.getTemplateId());
        BlockState state = level.getBlockState(pos);
        if (!settings.canLight() || !state.is(Blocks.CAMPFIRE) || !CampfireBlock.canLight(state)
            || !canReachFire(npc, pos) || !EventHooks.canEntityGrief(level, npc)) return false;
        if (!level.setBlock(pos, state.setValue(CampfireBlock.LIT, true), 3)) return false;
        npc.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1, 1);
        level.gameEvent(npc, net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos);
        return true;
    }
}
