package mod.cdv.tnpc.travel;

import java.util.*;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.offline.OfflineConfig;
import mod.cdv.tnpc.quest.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** No chunk tickets, locate calls, or ticking surrogate entities. */
public final class NpcTravel {
    public static final String EPOCH = "tnpcTravelEpoch";
    public static final String ARRIVAL_COOLDOWN = "tnpcTravelCooldownUntil";
    private static MinecraftServer queueServer;
    private static final ArrayDeque<UUID> queue = new ArrayDeque<>();
    private static final Set<UUID> scheduled = new HashSet<>();
    private static boolean snapshotting;
    private NpcTravel() {}
    public static void reset() { queueServer = null; queue.clear(); scheduled.clear(); snapshotting = false; }
    static void recordChanged(UUID id) {
        if (queueServer != null && scheduled.add(id)) queue.addLast(id);
    }

    public static boolean eligible(AutoGunOperator npc, ServerLevel level) {
        var p = OfflineConfig.forLoadout(npc.getTemplateId());
        var a = p.actions().get("move");
        var active = npc.getQuestState().active();
        var q = active == null ? null : QuestConfig.settings().quests().get(active.id());
        if (!TravelConfig.settings().enabled() || !QuestConfig.settings().enabled() || !p.enabled()
            || a == null || !a.enabled() || q == null || !q.enabled()
            || npc.getHealth() <= 0 || (npc.isRemoved() && npc.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK)
            || now(level.getServer()) < npc.getPersistentData().getLong(ARRIVAL_COOLDOWN)
            || npc.isNoAi() || npc.getTarget() != null || npc.isOnAlert() || npc.isOnFire()
            || npc.isPassenger() || npc.isVehicle() || npc.isLeashed()
            || mod.cdv.tnpc.dialogue.NpcConversations.partner(npc) != null) return false;
        return noPlayerNear(level, npc.position(), TravelConfig.settings().playerExclusionRadius());
    }
    private static boolean noPlayerNear(ServerLevel level, Vec3 pos, double radius) {
        return level.players().stream().noneMatch(p -> p.distanceToSqr(pos) < radius * radius);
    }
    public static boolean depart(AutoGunOperator npc, ServerLevel level, boolean alreadyUnloaded) {
        if (!eligible(npc, level) || !hidden(level, npc)) return false;
        var ledger = TravelLedger.get(level.getServer());
        var old = ledger.get(npc.getUUID());
        if (old != null && old.phase == TravelOwnership.Phase.TRAVELLING) return false;
        long previous = Math.max(npc.getPersistentData().getLong(EPOCH), old == null ? 0 : old.epoch);
        if (previous == Long.MAX_VALUE) return false;
        // Snapshot first. An exception cannot remove the original entity.
        CompoundTag saved = snapshot(npc);
        saved.putLong(EPOCH, previous + 1);
        var entry = new TravelLedger.Entry(npc.getUUID(), previous + 1, TravelOwnership.Phase.TRAVELLING,
            level.dimension().location().toString(), saved, now(level.getServer()));
        ledger.put(entry);
        try { ledger.flush(level.getServer()); }
        catch (java.io.UncheckedIOException failure) {
            ledger.rollback(npc.getUUID(), old);
            com.mojang.logging.LogUtils.getLogger().error("NPC departure not committed: {}", npc.getUUID(), failure);
            return false;
        }
        if (!alreadyUnloaded) npc.discard();
        return true;
    }
    private static CompoundTag snapshot(AutoGunOperator npc) {
        boolean was = snapshotting; snapshotting = true;
        try { return npc.saveWithoutId(new CompoundTag()); }
        finally { snapshotting = was; }
    }
    /** Called at the end of the entity save hook. Does not recursively serialize the entity. */
    public static void saved(AutoGunOperator npc, CompoundTag tag, ServerLevel level) {
        if (snapshotting) return;
        var ledger = TravelLedger.get(level.getServer()); var e = ledger.get(npc.getUUID());
        if (e == null || e.epoch != npc.getPersistentData().getLong(EPOCH)
            || e.phase != TravelOwnership.Phase.RESIDENT) return;
        e.npc = tag.copy(); e.dimension = level.dimension().location().toString();
        e.updatedAt = now(level.getServer()); ledger.setDirty();
    }
    public static boolean mayJoin(AutoGunOperator npc, ServerLevel level) {
        var ledger = TravelLedger.get(level.getServer()); var e = ledger.get(npc.getUUID());
        if (e == null) return true;
        return TravelOwnership.mayJoin(e.phase, e.epoch, npc.getPersistentData().getLong(EPOCH),
            e.dimension.equals(level.dimension().location().toString()), npc.position().distanceToSqr(position(e.npc)));
    }
    public static void removed(AutoGunOperator npc, ServerLevel level) {
        var e = TravelLedger.get(level.getServer()).get(npc.getUUID());
        if (e == null || e.epoch != npc.getPersistentData().getLong(EPOCH)
            || e.phase == TravelOwnership.Phase.TRAVELLING) return;
        var reason = npc.getRemovalReason();
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            e.phase = TravelOwnership.Phase.DEAD; e.npc = new CompoundTag();
            TravelLedger.get(level.getServer()).flush(level.getServer());
        }
    }
    public static void died(AutoGunOperator npc, ServerLevel level) {
        var ledger = TravelLedger.get(level.getServer()); var e = ledger.get(npc.getUUID());
        if (e != null && e.epoch == npc.getPersistentData().getLong(EPOCH)) {
            e.phase = TravelOwnership.Phase.DEAD; e.npc = new CompoundTag(); ledger.flush(level.getServer());
        }
    }
    public static void tick(MinecraftServer server) {
        if (now(server) % TravelConfig.settings().intervalTicks() != 0) return;
        var ledger = TravelLedger.get(server);
        if (queueServer != server) {
            reset(); queueServer = server;
            ledger.entries().stream().filter(e -> e.phase != TravelOwnership.Phase.DEAD).forEach(e -> recordChanged(e.uuid));
        }
        int count = Math.min(queue.size(), TravelConfig.settings().recordsPerPass());
        for (int i = 0; i < count; i++) {
            UUID id = queue.removeFirst(); scheduled.remove(id);
            var e = ledger.get(id);
            if (e == null || e.phase == TravelOwnership.Phase.DEAD) continue;
            recordChanged(id);
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(e.dimension)));
            if (level == null) continue;
            try {
                if (e.phase == TravelOwnership.Phase.TRAVELLING) {
                    if (TravelConfig.settings().enabled() || e.recall) resolve(e, level, ledger);
                }
                else recover(e, level, ledger);
            } catch (RuntimeException failure) {
                com.mojang.logging.LogUtils.getLogger().error("Travel record remains pending: {}", e.uuid, failure);
            }
        }
    }
    private static AutoGunOperator restore(TravelLedger.Entry e, ServerLevel level) {
        var npc = new AutoGunOperator(AutoGunOperator.NPC_TYPE, level);
        npc.load(e.npc.copy()); npc.setUUID(e.uuid); npc.getPersistentData().putLong(EPOCH, e.epoch);
        return npc;
    }
    private static void resolve(TravelLedger.Entry e, ServerLevel level, TravelLedger ledger) {
        // Restoring a non-added object uses no entity tick; it reuses the exact quest/inventory codecs.
        AutoGunOperator npc = restore(e, level);
        var profile = OfflineConfig.forLoadout(npc.getTemplateId()); var action = profile.actions().get("move");
        var active = npc.getQuestState().active();
        var q = active == null ? null : QuestConfig.settings().quests().get(active.id());
        // A removed/disabled quest returns the NPC to its origin rather than trapping its inventory forever.
        if (e.recall || q == null || !q.enabled()) { returnToOrigin(e, npc, level, ledger); return; }
        if (!QuestConfig.settings().enabled() || !profile.enabled() || action == null || !action.enabled()) return;
        npc.getOfflineActions().observe(npc, level);
        for (int i = 0; i < TravelConfig.settings().sitesPerRecord(); i++) {
            BlockPos pos = candidate(level, q, i);
            if (pos == null || !safe(level, npc, pos)) continue;
            var place = QuestLocation.inspect(level, pos, List.of(q));
            if (!q.completedAt(active, place)) continue;
            Vec3 destination = Vec3.atBottomCenterOf(pos);
            long cost = TravelMath.cost(horizontal(npc.position(), destination), TravelConfig.settings().blocksPerPoint(),
                action.cost(), OfflineConfig.settings().ticksPerPoint());
            if (npc.getOfflineActions().wallet().balance() < cost) continue;
            npc.moveTo(destination.x, destination.y, destination.z, npc.getYRot(), 0);
            if (!hidden(level, npc)) { npc.load(e.npc.copy()); npc.getOfflineActions().observe(npc, level); continue; }
            npc.getOfflineActions().travelPaid(cost);
            // Reward and next quest are inside the arrival snapshot, before the entity is added.
            NpcQuests.check(npc, level);
            commitArrival(e, npc, level, ledger); return;
        }
    }
    private static void returnToOrigin(TravelLedger.Entry e, AutoGunOperator npc, ServerLevel level, TravelLedger ledger) {
        if (!safe(level, npc, npc.blockPosition()) || !hidden(level, npc)) return;
        if (!e.recall) npc.getQuestState().clear(0);
        npc.getOfflineActions().observe(npc, level);
        commitArrival(e, npc, level, ledger);
    }
    private static void commitArrival(TravelLedger.Entry e, AutoGunOperator npc, ServerLevel level, TravelLedger ledger) {
        npc.getPersistentData().putLong(ARRIVAL_COOLDOWN,
            TravelMath.credit(now(level.getServer()), TravelConfig.settings().arrivalCooldownTicks()));
        npc.setDeltaMovement(Vec3.ZERO);
        npc.fallDistance = 0;
        CompoundTag previousNpc = e.npc; boolean previousRecall = e.recall; long previousUpdated = e.updatedAt;
        e.recall = false; e.npc = snapshot(npc); e.phase = TravelOwnership.Phase.ARRIVING; e.updatedAt = now(level.getServer());
        try { ledger.flush(level.getServer()); }
        catch (java.io.UncheckedIOException failure) {
            e.npc = previousNpc; e.recall = previousRecall; e.updatedAt = previousUpdated;
            e.phase = TravelOwnership.Phase.TRAVELLING; ledger.setDirty();
            throw failure;
        }
        if (level.addFreshEntity(npc)) {
            e.phase = TravelOwnership.Phase.RESIDENT; ledger.flush(level.getServer());
        }
    }
    private static void recover(TravelLedger.Entry e, ServerLevel level, TravelLedger ledger) {
        // Both terrain and entity storage must be ready before absence is meaningful.
        Vec3 point = position(e.npc); BlockPos pos = BlockPos.containing(point);
        if (level.getEntity(e.uuid) != null || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
            || !level.areEntitiesLoaded(new ChunkPos(pos).toLong()) || !level.isPositionEntityTicking(pos)) return;
        var npc = restore(e, level);
        if (!safe(level, npc, pos) || !hidden(level, npc)) return;
        if (level.addFreshEntity(npc)) { e.phase = TravelOwnership.Phase.RESIDENT; ledger.flush(level.getServer()); }
    }
    private static BlockPos candidate(ServerLevel level, QuestDefinition q, int attempt) {
        if (level.players().isEmpty()) return null;
        var player = level.players().get(level.random.nextInt(level.players().size()));
        int radius = TravelConfig.settings().playerExclusionRadius() + 16;
        double angle = level.random.nextDouble() * Math.PI * 2;
        BlockPos p = player.blockPosition().offset((int)(Math.cos(angle) * radius), 0, (int)(Math.sin(angle) * radius));
        var chunk = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
        if (chunk == null || !level.areEntitiesLoaded(new ChunkPos(p).toLong())) return null;
        if (q.objective().type() == QuestDefinition.Type.POI) {
            try (var records = level.getPoiManager().getInChunk(holder -> holder.unwrapKey()
                .map(key -> q.objective().targets().contains(key.location().toString())).orElse(false),
                new ChunkPos(p), PoiManager.Occupancy.ANY)) {
                var poi = records.findFirst();
                if (poi.isEmpty()) return null;
                return poi.get().getPos().offset(2, 0, 0);
            }
        }
        int y = attempt % 2 == 0 ? chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX() & 15, p.getZ() & 15) + 1
            : player.blockPosition().getY();
        for (int dy = 0; dy < 8; dy++) {
            BlockPos result = new BlockPos(p.getX(), y - dy, p.getZ());
            if (level.getBlockState(result.below()).isFaceSturdy(level, result.below(), Direction.UP)) return result;
        }
        return null;
    }
    private static boolean safe(ServerLevel level, AutoGunOperator npc, BlockPos p) {
        if (p.getY() <= level.getMinBuildHeight() || p.getY() + 2 >= level.getMaxBuildHeight()
            || !level.getWorldBorder().isWithinBounds(p)) return false;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (level.getChunkSource().getChunkNow((p.getX() + x) >> 4, (p.getZ() + z) >> 4) == null) return false;
        if (!level.areEntitiesLoaded(new ChunkPos(p).toLong()) || !level.isPositionEntityTicking(p)) return false;
        var floor = level.getBlockState(p.below());
        if (!floor.isFaceSturdy(level, p.below(), Direction.UP) || floor.is(Blocks.MAGMA_BLOCK)
            || floor.is(Blocks.CACTUS) || floor.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock) return false;
        if (!level.getFluidState(p).isEmpty() || !level.getFluidState(p.above()).isEmpty()
            || hazardous(level, p) || hazardous(level, p.above())) return false;
        var box = npc.getType().getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(p));
        return level.noCollision(npc, box) && level.getEntities(npc, box,
            other -> other instanceof net.minecraft.world.entity.LivingEntity || other.canBeCollidedWith()).isEmpty();
    }
    private static boolean hazardous(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE);
    }
    private static boolean hidden(ServerLevel level, AutoGunOperator npc) {
        if (!noPlayerNear(level, npc.position(), TravelConfig.settings().playerExclusionRadius())) return false;
        for (var player : level.players()) {
            if (player.distanceToSqr(npc) > 128 * 128) continue;
            // A visibility ray must never cross unloaded terrain.
            for (int x = Math.min(player.blockPosition().getX(), npc.blockPosition().getX()) >> 4;
                 x <= Math.max(player.blockPosition().getX(), npc.blockPosition().getX()) >> 4; x++)
                for (int z = Math.min(player.blockPosition().getZ(), npc.blockPosition().getZ()) >> 4;
                     z <= Math.max(player.blockPosition().getZ(), npc.blockPosition().getZ()) >> 4; z++)
                    if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            if (player.hasLineOfSight(npc)) return false;
        }
        return true;
    }
    private static Vec3 position(CompoundTag tag) {
        var pos = tag.getList("Pos", net.minecraft.nbt.Tag.TAG_DOUBLE);
        return new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
    }
    private static double horizontal(Vec3 a, Vec3 b) { return Math.hypot(a.x - b.x, a.z - b.z); }
    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
}
