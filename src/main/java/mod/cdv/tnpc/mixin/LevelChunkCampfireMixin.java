package mod.cdv.tnpc.mixin;

import mod.cdv.tnpc.campfire.CampfireTimers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes real state changes, including placement, ignition, water and /setblock. */
@Mixin(value = LevelChunk.class, remap = false)
public abstract class LevelChunkCampfireMixin {
    @Shadow public abstract Level getLevel();
    @Inject(method = "setBlockState", at = @At("RETURN"), remap = false)
    private void tnpc$campfireChanged(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> result) {
        BlockState before = result.getReturnValue();
        if (before != null && (before.is(Blocks.CAMPFIRE) || state.is(Blocks.CAMPFIRE)) && getLevel() instanceof ServerLevel level)
            CampfireTimers.blockChanged(level, pos, before, state);
    }
}
