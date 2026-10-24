package mod.cdv.tnpc.entity.ai;

import net.minecraft.world.phys.Vec3;

/** Short-lived incident memory, deliberately independent of an attacker's location. */
public final class DamageEscapeMemory {
    private Vec3 origin;
    private long until;
    private long revision;

    public void remember(Vec3 incident, long now, int duration) {
        origin = incident;
        until = now + duration;
        revision++;
    }

    public boolean active(long now) { return origin != null && now < until; }
    public Vec3 origin() { return origin; }
    public long revision() { return revision; }
    public void clear() { origin = null; until = 0; }
}
