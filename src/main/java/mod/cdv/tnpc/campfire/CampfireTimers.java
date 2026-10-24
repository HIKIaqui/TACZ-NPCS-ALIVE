package mod.cdv.tnpc.campfire;

import java.util.*;
import mod.cdv.tnpc.data.NpcBehaviorConfig;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;

/** One saved ledger per dimension. Unloaded chunks never get force-loaded by a timer. */
public final class CampfireTimers extends SavedData {
    private final CampfireClock clock = new CampfireClock();
    private final Set<Long> loadedChunks = new HashSet<>();
    private final LinkedHashSet<Long> pendingScans = new LinkedHashSet<>();
    private Boolean enabledLastTick;

    public static CampfireTimers get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(CampfireTimers::new, CampfireTimers::load), "tacznpcs_campfires");
    }
    private static CampfireTimers load(CompoundTag tag, HolderLookup.Provider lookup) {
        CampfireTimers data = new CampfireTimers();
        long[] positions = tag.getLongArray("positions"), deadlines = tag.getLongArray("deadlines");
        for (int i = 0; i < Math.min(positions.length, deadlines.length); i++) {
            BlockPos pos = BlockPos.of(positions[i]);
            data.clock.restart(positions[i], new ChunkPos(pos).toLong(), deadlines[i]);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        Map<Long, Long> records = clock.snapshot(); long[] positions = new long[records.size()], deadlines = new long[records.size()];
        int i = 0;
        for (var entry : records.entrySet()) { positions[i] = entry.getKey(); deadlines[i++] = entry.getValue(); }
        tag.putLongArray("positions", positions); tag.putLongArray("deadlines", deadlines); return tag;
    }
    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        // Generation on worker threads is discovered by the chunk-load scan instead.
        if (!level.getServer().isSameThread()) return;
        if (!before.is(Blocks.CAMPFIRE) && !after.is(Blocks.CAMPFIRE)) return;
        CampfireTimers data = get(level);
        if (!after.is(Blocks.CAMPFIRE) || !after.getValue(CampfireBlock.LIT)
            || !NpcBehaviorConfig.campfires().extinguishAfterTime()) {
            if (data.clock.forget(pos.asLong())) data.setDirty();
        } else if (!before.is(Blocks.CAMPFIRE) || !before.getValue(CampfireBlock.LIT)) {
            data.clock.restart(pos.asLong(), new ChunkPos(pos).toLong(), level.getGameTime() + NpcBehaviorConfig.campfires().timeToExtinguish());
            data.setDirty();
        } else data.observe(level, pos);
    }
    private void observe(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT) && NpcBehaviorConfig.campfires().extinguishAfterTime()) {
            if (clock.observe(pos.asLong(), new ChunkPos(pos).toLong(), level.getGameTime(), NpcBehaviorConfig.campfires().timeToExtinguish())) setDirty();
        } else if (clock.forget(pos.asLong())) setDirty();
    }
    public boolean hasPendingScanNear(BlockPos pos, int radius) {
        for (int x = (pos.getX() - radius) >> 4; x <= (pos.getX() + radius) >> 4; x++)
            for (int z = (pos.getZ() - radius) >> 4; z <= (pos.getZ() + radius) >> 4; z++)
                if (pendingScans.contains(ChunkPos.asLong(x, z))) return true;
        return false;
    }
    public void chunkLoaded(long chunk) { loadedChunks.add(chunk); pendingScans.add(chunk); }
    public void chunkUnloaded(long chunk) { loadedChunks.remove(chunk); pendingScans.remove(chunk); }
    public void tick(ServerLevel level) {
        boolean enabled = NpcBehaviorConfig.campfires().extinguishAfterTime();
        if (!Objects.equals(enabledLastTick, enabled)) {
            enabledLastTick = enabled;
            if (!enabled) { if (!clock.isEmpty()) { clock.clear(); setDirty(); } }
            else pendingScans.addAll(loadedChunks);
        }
        for (int count = 0; count < 2 && !pendingScans.isEmpty(); count++) {
            long key = pendingScans.iterator().next(); pendingScans.remove(key);
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk == null) continue;
            // Also validate saved positions whose block entity was removed by an external edit.
            for (long position : clock.positionsInChunk(key)) observe(level, BlockPos.of(position));
            for (BlockPos pos : chunk.getBlockEntitiesPos()) if (level.getBlockState(pos).is(Blocks.CAMPFIRE)) {
                // Existing-world campfires also become discoverable, even with the timer disabled.
                if (level.getPoiManager().getType(pos).isEmpty()) level.getPoiManager().add(pos, CampfirePois.CAMPFIRE);
                if (enabled) observe(level, pos);
            }
        }
        if (!enabled) return;
        for (CampfireClock.Due entry : clock.pollDue(level.getGameTime(), 128)) {
            BlockPos pos = BlockPos.of(entry.position());
            if (!level.hasChunkAt(pos)) continue; // The ledger survives; chunkLoaded requeues it.
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
                CampfireBlock.dowse(null, level, pos, state);
                level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
            if (clock.forget(entry.position())) setDirty();
        }
    }
}
