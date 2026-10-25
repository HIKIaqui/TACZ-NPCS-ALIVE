package mod.cdv.tnpc.entity.ai;

import java.util.UUID;
import java.util.function.IntUnaryOperator;
import mod.cdv.tnpc.data.CombatSettings;

/** Transient combat state. Stopping a movement goal cannot reset the first-shot gate. */
public final class CombatMemory {
    private UUID target;
    private long readyAt, lastSeenAt, nextShotAt;
    public long readyAt() { return readyAt; }
    public long nextShotAt() { return nextShotAt; }
    public void acquired(UUID id, long now, CombatSettings.Profile profile, IntUnaryOperator random) {
        if (id == null) return;
        if (!id.equals(target) || expired(now, profile.targetMemoryTicks())) {
            target = id; readyAt = deadline(now, profile.reactionTimeTicks().sample(random)); lastSeenAt = now;
        }
    }
    public void observe(UUID id, boolean visible, long now, CombatSettings.Profile profile, IntUnaryOperator random) {
        if (id == null) return;
        if (!id.equals(target)) acquired(id, now, profile, random);
        if (visible) {
            if (expired(now, profile.targetMemoryTicks())) readyAt = deadline(now, profile.reactionTimeTicks().sample(random));
            lastSeenAt = now;
        }
    }
    private boolean expired(long now, int memory) { return now < lastSeenAt || now - lastSeenAt > (long)memory + 1; }
    public boolean mayFire(UUID id, boolean visible, long now) {
        return visible && id != null && id.equals(target) && now >= readyAt && now >= nextShotAt;
    }
    public void fired(long now, int interval) { nextShotAt = deadline(now, interval); }
    private static long deadline(long now, int ticks) { return now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks; }
}
