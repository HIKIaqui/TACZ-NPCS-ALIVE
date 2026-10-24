package mod.cdv.tnpc.offline;

import java.util.*;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.campfire.NpcCampfires;
import mod.cdv.tnpc.data.NpcBehaviorConfig;
import mod.cdv.tnpc.data.NpcRelations;
import mod.cdv.tnpc.data.TemplateDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Entity-owned state. Accounts immediately on join, applies effects only on entity ticks. */
public final class OfflineActions {
    private final OfflineWallet wallet = new OfflineWallet();
    private boolean pending;
    private int delay;
    private final Map<String, Integer> failures = new HashMap<>();
    private String lastAction = "none";
    public OfflineWallet wallet() { return wallet; }
    public String lastAction() { return lastAction; }
    public void travelPaid(long cost) {
        if (!wallet.spend(cost)) throw new IllegalStateException("Travel payment unavailable");
        lastAction = "move";
    }
    public void initialize(ServerLevel level) {
        wallet.initialize(now(level), OfflineGrants.get(level.getServer()).total());
    }
    private static long now(ServerLevel level) { return level.getServer().overworld().getGameTime(); }
    public void observe(AutoGunOperator npc, ServerLevel level) {
        long before = wallet.balance();
        wallet.observe(now(level), OfflineGrants.get(level.getServer()).total(),
            OfflineConfig.forLoadout(npc.getTemplateId()).enabled());
        if (wallet.balance() > before) { pending = true; delay = 20; failures.clear(); }
    }
    public void save(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putLong("lastTick", wallet.lastTick()); data.putLong("balanceTicks", wallet.balance());
        data.putLong("grantSeen", wallet.grantSeen()); data.putBoolean("pending", pending);
        data.putString("lastAction", lastAction); tag.put("tnpcOfflineActions", data);
    }
    public void load(CompoundTag tag, ServerLevel level) {
        if (!tag.contains("tnpcOfflineActions")) {
            initialize(level);
            // Existing saves predate the feature; do not invent their absence duration.
            // They still receive test grants issued after this feature was installed.
            if (tag.getBoolean("templateInitialized")) wallet.restore(now(level), 0, 0);
            return;
        }
        CompoundTag data = tag.getCompound("tnpcOfflineActions");
        wallet.restore(data.getLong("lastTick"), data.getLong("balanceTicks"), data.getLong("grantSeen"));
        pending = data.getBoolean("pending"); lastAction = data.getString("lastAction"); delay = 0;
    }
    public void tick(AutoGunOperator npc, ServerLevel level) {
        observe(npc, level);
        if (!pending || !npc.isAlive() || npc.isNoAi() || !OfflineConfig.forLoadout(npc.getTemplateId()).enabled()) return;
        if (delay-- > 0) return;
        delay = 20;
        // Never apply deferred peaceful actions while the NPC is fighting or escaping.
        if (npc.getTarget() != null || npc.isOnAlert() || npc.isOnFire()) return;
        List<String> candidates = new ArrayList<>();
        var profile = OfflineConfig.forLoadout(npc.getTemplateId());
        for (var entry : profile.actions().entrySet()) {
            var action = entry.getValue();
            if (action.enabled() && wallet.balance() >= cost(entry.getKey(), profile) && failures.getOrDefault(entry.getKey(), 0) < 3 && eligible(entry.getKey(), npc))
                candidates.add(entry.getKey());
        }
        if (candidates.isEmpty()) { pending = false; return; }
        // One attempt per NPC per second and a shared world budget prevent load spikes.
        if (!OfflineEvents.claimAttempt(level.getServer())) return;
        int total = candidates.stream().mapToInt(id -> profile.actions().get(id).weight()).sum();
        int roll = npc.getRandom().nextInt(total); String chosen = candidates.get(0);
        for (String id : candidates) {
            roll -= profile.actions().get(id).weight(); if (roll < 0) { chosen = id; break; }
        }
        // A trip pays for its full distance at arrival, not for each processing pass.
        if (chosen.equals("move")) {
            if (!mod.cdv.tnpc.travel.NpcTravel.depart(npc, level, false)) failures.merge(chosen, 1, Integer::sum);
            return;
        }
        boolean success = switch (chosen) {
            case "rest" -> { npc.setHealth(npc.getMaxHealth()); yield true; }
            case "placeCampfire" -> placeCampfire(npc, level);
            case "meetAllies" -> meetAllies(npc, level);
            case "loot" -> OfflineLoot.loot(npc);
            case "share" -> share(npc, level);
            default -> false;
        };
        if (success) { wallet.spend(cost(chosen, profile)); lastAction = chosen; }
        // Failed world mutations cost nothing. Bounded retries stop blocked sites spinning forever.
        else failures.merge(chosen, 1, Integer::sum);
    }
    private long cost(String id, OfflineConfig.Profile profile) {
        return (long)OfflineConfig.settings().ticksPerPoint() * profile.actions().get(id).cost();
    }
    private boolean eligible(String id, AutoGunOperator npc) {
        return switch (id) {
            case "move" -> npc.level() instanceof ServerLevel level && mod.cdv.tnpc.travel.NpcTravel.eligible(npc, level);
            case "rest" -> npc.getHealth() < npc.getMaxHealth();
            case "placeCampfire" -> NpcBehaviorConfig.campfireFor(npc.getTemplateId()).canCreate();
            case "meetAllies" -> !alliedLoadouts(npc).isEmpty();
            case "loot" -> OfflineLoot.hasPools(npc);
            case "share" -> mod.cdv.tnpc.inventory.NpcInventoryConfig.forLoadout(npc.getTemplateId()).shareItems()
                && !npc.getNpcInventory().cargoSnapshot().isEmpty();
            default -> false;
        };
    }
    private boolean placeCampfire(AutoGunOperator npc, ServerLevel level) {
        for (int i = 0; i < 16; i++) {
            BlockPos pos = npc.blockPosition().offset(npc.getRandom().nextInt(7) - 3,
                npc.getRandom().nextInt(3) - 1, npc.getRandom().nextInt(7) - 3);
            if (!new AABB(pos).intersects(npc.getBoundingBox())
                && loadedRouteArea(level, npc.blockPosition(), pos) && NpcCampfires.build(level, npc, pos)) return true;
        }
        return false;
    }
    private boolean share(AutoGunOperator npc, ServerLevel level) {
        var nearby = level.getEntitiesOfClass(AutoGunOperator.class, npc.getBoundingBox().inflate(6),
            other -> other != npc && other.isAlive());
        nearby.sort(java.util.Comparator.comparingDouble(npc::distanceToSqr));
        for (var other : nearby) if (mod.cdv.tnpc.inventory.NpcItemActions.shareOffline(npc, other)) return true;
        return false;
    }
    private boolean meetAllies(AutoGunOperator npc, ServerLevel level) {
        List<String> loadouts = alliedLoadouts(npc);
        if (loadouts.isEmpty()) return false;
        var settings = OfflineConfig.settings();
        AABB area = npc.getBoundingBox().inflate(settings.populationRadius());
        int nearby = level.getEntitiesOfClass(AutoGunOperator.class, area, AutoGunOperator::isAlive).size();
        int wanted = Math.min(1 + npc.getRandom().nextInt(2), settings.nearbyNpcLimit() - nearby);
        if (wanted <= 0) return false;
        int spawned = 0;
        for (int i = 0; i < 24 && spawned < wanted; i++) {
            BlockPos pos = npc.blockPosition().offset(npc.getRandom().nextInt(13) - 6,
                npc.getRandom().nextInt(5) - 2, npc.getRandom().nextInt(13) - 6);
            if (!safeChunkArea(level, pos)) continue;
            if (!level.getWorldBorder().isWithinBounds(pos) || pos.getY() <= level.getMinBuildHeight()
                || pos.getY() + 2 >= level.getMaxBuildHeight()
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                || level.getBlockState(pos.below()).is(Blocks.MAGMA_BLOCK)
                || level.getBlockState(pos.below()).getBlock() instanceof net.minecraft.world.level.block.CampfireBlock
                || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) continue;
            Vec3 location = Vec3.atBottomCenterOf(pos);
            AABB box = AutoGunOperator.NPC_TYPE.getDimensions().makeBoundingBox(location);
            if (box.intersects(npc.getBoundingBox()) || !level.noCollision(npc, box) || !level.getEntities(npc, box).isEmpty()) continue;
            String loadout = loadouts.get(npc.getRandom().nextInt(loadouts.size()));
            AutoGunOperator ally = AutoGunOperator.create(level, loadout);
            ally.moveTo(location.x, location.y, location.z, npc.getYRot(), 0);
            if (level.addFreshEntity(ally)) spawned++;
        }
        return spawned > 0;
    }
    private static List<String> alliedLoadouts(AutoGunOperator npc) {
        return NpcRelations.alliedLoadouts(npc.getTemplateId(), npc.relationshipTemplate(), TemplateDataManager.TEMPLATES,
            NpcRelations.entityGroups(npc.getType(), TemplateDataManager.FACTIONS));
    }
    private static boolean safeChunkArea(ServerLevel level, BlockPos pos) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (!level.hasChunkAt(pos.offset(x, 0, z))) return false;
        return true;
    }
    private static boolean loadedRouteArea(ServerLevel level, BlockPos from, BlockPos to) {
        // The campfire helper raycasts between the actor and the site. Check every
        // crossed chunk first, including diagonal intermediate chunks and the floor margin.
        for (int x = (Math.min(from.getX(), to.getX()) - 1) >> 4;
             x <= (Math.max(from.getX(), to.getX()) + 1) >> 4; x++)
            for (int z = (Math.min(from.getZ(), to.getZ()) - 1) >> 4;
                 z <= (Math.max(from.getZ(), to.getZ()) + 1) >> 4; z++)
                if (!level.hasChunk(x, z)) return false;
        return true;
    }
}
