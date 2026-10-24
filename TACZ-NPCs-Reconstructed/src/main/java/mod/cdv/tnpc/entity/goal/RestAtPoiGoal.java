package mod.cdv.tnpc.entity.goal;

import java.util.*;
import javax.annotation.Nullable;
import mod.cdv.tnpc.campfire.*;
import mod.cdv.tnpc.data.NpcBehaviorConfig;
import mod.cdv.tnpc.data.NpcBehaviorConfig.RestSettings;
import mod.cdv.tnpc.data.NpcBehaviorConfig.CampfireSettings;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.village.poi.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.EventHooks;

/** Walk to an existing campsite or prepare one; perform fire actions before actual rest. */
public class RestAtPoiGoal extends Goal {
    private enum Phase { MOVING, BUILDING, LIGHTING, RESTING, STOPPED }
    private final AutoGunOperator op;
    private long nextAttemptTick, travelDeadline, restUntil, actionUntil, nextSocialScanTick;
    private Phase phase = Phase.STOPPED;
    private boolean plannedNew;
    @Nullable private BlockPos meetingPoint, destination;
    @Nullable private Path path;
    @Nullable private AutoGunOperator socialPartner;
    private RestSettings settings;
    private CampfireSettings campfire;

    public RestAtPoiGoal(AutoGunOperator op) {
        this.op = op;
        settings = NpcBehaviorConfig.restFor("default");
        campfire = NpcBehaviorConfig.campfireFor("default");
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    private boolean isPeaceful() {
        return op.canBeginRest() && !op.isAggressive() && !op.isOnFire()
            && op.getDataHolder().reloadStateType == com.tacz.guns.api.entity.ReloadState.StateType.NOT_RELOADING
            && (op.getLastHurtByMob() == null || op.tickCount - op.getLastHurtByMobTimestamp() > 100);
    }
    private boolean accepts(Holder<PoiType> type) {
        if (type.is(CampfirePois.CAMPFIRE.getKey())) return campfire.useAsRestPoi();
        return type.unwrapKey().map(key -> settings.poiTypes().contains(key.location())).orElse(false);
    }
    @Override public boolean canUse() {
        long now = op.level().getGameTime();
        if (!(op.level() instanceof ServerLevel level) || !isPeaceful() || now < nextAttemptTick) return false;
        settings = NpcBehaviorConfig.restFor(op.getTemplateId());
        campfire = NpcBehaviorConfig.campfireFor(op.getTemplateId());
        nextAttemptTick = now + settings.searchIntervalTicks();
        if (!settings.enabled() || op.getRandom().nextDouble() >= settings.chance()) return false;
        meetingPoint = null; destination = null; path = null; plannedNew = false;
        boolean canLight = campfire.canLight() && EventHooks.canEntityGrief(level, op);
        List<BlockPos> points = level.getPoiManager().getInRange(this::accepts, op.blockPosition(), settings.searchRadius(), PoiManager.Occupancy.ANY)
            .map(poi -> poi.getPos().immutable()).filter(level::hasChunkAt)
            .sorted(Comparator.<BlockPos>comparingInt(pos -> level.getBlockState(pos).is(Blocks.CAMPFIRE)
                && !level.getBlockState(pos).getValue(CampfireBlock.LIT) ? 1 : 0)
                .thenComparingDouble(pos -> pos.distSqr(op.blockPosition())))
            .limit(16).toList();
        for (BlockPos point : points) {
            boolean fire = level.getBlockState(point).is(Blocks.CAMPFIRE);
            if (fire && !NpcCampfires.isUsable(level.getBlockState(point), canLight)) continue;
            if (CampfireEvents.RESERVATIONS.hasRoom(NpcCampfires.site(level, point, fire), op.getUUID(), capacity(fire), now)
                && findRestSpot(level, point)) { meetingPoint = point; return true; }
        }
        if (!campfire.useAsRestPoi() || !campfire.canCreate() || !EventHooks.canEntityGrief(level, op)
            || now < op.getPersistentData().getLong("tnpcNextCampfire")) return false;
        for (int attempt = 0; attempt < 24; attempt++) {
            int dx = op.getRandom().nextInt(-8, 9), dz = op.getRandom().nextInt(-8, 9);
            if (dx * dx + dz * dz < 9) continue;
            for (int dy : new int[] {0, -1, 1, -2, 2}) {
                BlockPos point = op.blockPosition().offset(dx, dy, dz);
                if (NpcCampfires.spaceForNew(level, op, point) && !NpcCampfires.fireNearby(level, op, point) && findRestSpot(level, point)) {
                    meetingPoint = point; plannedNew = true; return true;
                }
            }
        }
        return false;
    }
    private int capacity(boolean fire) { return fire ? NpcBehaviorConfig.campfires().maxResters() : 16; }
    private boolean findRestSpot(ServerLevel level, BlockPos point) {
        destination = null; path = null;
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = op.getRandom().nextInt(-3, 4), dz = op.getRandom().nextInt(-3, 4);
            if (dx * dx + dz * dz < 4) continue;
            for (int dy : new int[] {0, -1, 1, -2}) {
                BlockPos spot = point.offset(dx, dy, dz);
                if (!level.getWorldBorder().isWithinBounds(spot) || !level.hasChunkAt(spot) || !level.hasChunkAt(spot.below())) continue;
                var floor = level.getBlockState(spot.below());
                if (!floor.isFaceSturdy(level, spot.below(), Direction.UP) || floor.getBlock() instanceof CampfireBlock
                    || !level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()
                    || !level.getBlockState(spot.above()).getCollisionShape(level, spot.above()).isEmpty()
                    || !level.getFluidState(spot).isEmpty()) continue;
                if (!CampfireEvents.RESERVATIONS.spotFree(level.dimension().location().toString(), NpcCampfires.spot(spot), op.getUUID(), level.getGameTime())) continue;
                if (!level.getEntitiesOfClass(AutoGunOperator.class,
                    op.getBoundingBox().move(spot.getX() + 0.5 - op.getX(), spot.getY() - op.getY(), spot.getZ() + 0.5 - op.getZ()).inflate(0.3), other -> other != op).isEmpty()) continue;
                Path candidate = op.getNavigation().createPath(spot, 0);
                if (candidate != null && candidate.canReach()) { destination = spot; path = candidate; return true; }
            }
        }
        return false;
    }
    @Override public void start() {
        op.endResting(); socialPartner = null; nextSocialScanTick = 0; restUntil = 0;
        ServerLevel level = (ServerLevel)op.level();
        boolean fire = plannedNew || level.getBlockState(meetingPoint).is(Blocks.CAMPFIRE);
        if (!CampfireEvents.RESERVATIONS.claim(op.getUUID(), NpcCampfires.site(level, meetingPoint, fire),
            NpcCampfires.spot(destination), capacity(fire), level.getGameTime())) { phase = Phase.STOPPED; return; }
        phase = Phase.MOVING;
        travelDeadline = level.getGameTime() + settings.travelTimeoutTicks();
        op.setSprinting(false); op.getNavigation().moveTo(path, settings.speed());
    }
    private boolean validMeeting(ServerLevel level) {
        if (meetingPoint == null || !level.hasChunkAt(meetingPoint)) return false;
        var state = level.getBlockState(meetingPoint);
        if (plannedNew && NpcCampfires.isReplaceableSpace(state)) return NpcBehaviorConfig.campfireFor(op.getTemplateId()).canCreate();
        if (state.is(Blocks.CAMPFIRE)) {
            var current = NpcBehaviorConfig.campfireFor(op.getTemplateId());
            return current.useAsRestPoi() && NpcCampfires.isUsable(state, current.canLight());
        }
        return !plannedNew && level.getPoiManager().getType(meetingPoint).filter(this::accepts).isPresent();
    }
    @Override public boolean canContinueToUse() {
        if (phase == Phase.STOPPED || !(op.level() instanceof ServerLevel level) || !isPeaceful()
            || !NpcBehaviorConfig.restFor(op.getTemplateId()).enabled() || destination == null || !validMeeting(level)
            || !CampfireEvents.RESERVATIONS.refresh(op.getUUID(), level.getGameTime())) return false;
        long now = level.getGameTime();
        if (restUntil != 0 && now >= restUntil) return false;
        return switch (phase) {
            case MOVING -> now < travelDeadline && (arrived() || !op.getNavigation().isDone());
            case BUILDING, LIGHTING -> now <= actionUntil + 20;
            case RESTING -> now < restUntil;
            default -> false;
        };
    }
    private boolean arrived() {
        return destination != null && op.distanceToSqr(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5) <= 1;
    }
    private void beginRest(long now) {
        phase = Phase.RESTING; op.beginResting();
        if (restUntil == 0) restUntil = now + roll(settings.minDurationTicks(), settings.maxDurationTicks());
    }
    private void beginAction(Phase action, long now) {
        op.endResting(); op.getNavigation().stop(); phase = action;
        actionUntil = now + (action == Phase.BUILDING ? campfire.buildDurationTicks() : campfire.igniteDurationTicks());
        if (action == Phase.BUILDING) op.getPersistentData().putLong("tnpcNextCampfire", now + campfire.creationCooldownTicks());
    }
    @Override public void tick() {
        if (phase == Phase.STOPPED || meetingPoint == null) return;
        ServerLevel level = (ServerLevel)op.level(); long now = level.getGameTime();
        if (!CampfireEvents.RESERVATIONS.refresh(op.getUUID(), now)) { phase = Phase.STOPPED; return; }
        var state = level.getBlockState(meetingPoint);
        if (phase == Phase.MOVING && arrived()) {
            op.getNavigation().stop();
            if (plannedNew && NpcCampfires.isReplaceableSpace(state)) beginAction(Phase.BUILDING, now);
            else if (state.is(Blocks.CAMPFIRE) && !state.getValue(CampfireBlock.LIT)) beginAction(Phase.LIGHTING, now);
            else beginRest(now);
        }
        if (phase == Phase.BUILDING || phase == Phase.LIGHTING) {
            op.getLookControl().setLookAt(meetingPoint.getX() + 0.5, meetingPoint.getY() + 0.5, meetingPoint.getZ() + 0.5, 30, 30);
            if (now % 20 == 0) op.swing(InteractionHand.MAIN_HAND);
            if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)) { plannedNew = false; beginRest(now); }
            else if (now >= actionUntil) {
                boolean success = phase == Phase.BUILDING ? NpcCampfires.build(level, op, meetingPoint) : NpcCampfires.light(level, op, meetingPoint);
                if (success) { plannedNew = false; beginRest(now); } else phase = Phase.STOPPED;
            }
            return;
        }
        if (phase != Phase.RESTING) return;
        if (state.is(Blocks.CAMPFIRE) && !state.getValue(CampfireBlock.LIT)) { beginAction(Phase.LIGHTING, now); return; }
        if (now >= nextSocialScanTick) {
            nextSocialScanTick = now + 40;
            socialPartner = settings.socialRadius() <= 0 ? null : level.getEntitiesOfClass(AutoGunOperator.class,
                op.getBoundingBox().inflate(settings.socialRadius()), other -> other != op && other.isAlive()
                    && other.getTarget() == null && other.getNavigation().isDone() && !op.isOpposedTo(other)
                    && !other.isOpposedTo(op) && op.getSensing().hasLineOfSight(other))
                .stream().min(Comparator.comparingDouble(op::distanceToSqr)).orElse(null);
        }
        if (socialPartner != null && socialPartner.isAlive() && socialPartner.getTarget() == null
            && op.distanceToSqr(socialPartner) <= settings.socialRadius() * settings.socialRadius()) {
            mod.cdv.tnpc.inventory.NpcItemActions.share(op, socialPartner);
            op.getLookControl().setLookAt(socialPartner, 20, 20);
        }
        else op.getLookControl().setLookAt(meetingPoint.getX() + 0.5, op.getEyeY(), meetingPoint.getZ() + 0.5, 20, 20);
    }
    private int roll(int min, int max) { return op.getRandom().nextInt(min, max + 1); }
    @Override public void stop() {
        if (op.getTarget() != null || op.hurtTime > 0 || op.isOnFire()) op.becomeAlert();
        op.endResting(); op.getNavigation().stop(); CampfireEvents.RESERVATIONS.release(op.getUUID());
        nextAttemptTick = op.level().getGameTime() + (restUntil != 0
            ? roll(settings.minCooldownTicks(), settings.maxCooldownTicks()) : settings.searchIntervalTicks());
        phase = Phase.STOPPED; socialPartner = null; path = null; meetingPoint = null; destination = null; plannedNew = false;
    }
}
