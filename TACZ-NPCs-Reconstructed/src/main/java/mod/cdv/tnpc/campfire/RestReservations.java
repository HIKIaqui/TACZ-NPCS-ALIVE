package mod.cdv.tnpc.campfire;

import java.util.*;

/** Short runtime leases; disappearing NPCs cannot reserve a campsite forever. */
public final class RestReservations {
    public record Site(String dimension, int x, int y, int z, boolean campfire) {}
    public record Spot(int x, int y, int z) {}
    private record Lease(Site site, Spot spot, long until) {}
    private final Map<UUID, Lease> leases = new HashMap<>();
    private void prune(long now) { leases.values().removeIf(lease -> lease.until() <= now); }
    public boolean spotFree(String dimension, Spot spot, UUID owner, long now) {
        prune(now);
        return leases.entrySet().stream().noneMatch(entry -> !entry.getKey().equals(owner)
            && entry.getValue().site().dimension().equals(dimension)
            && entry.getValue().spot().x() == spot.x() && entry.getValue().spot().z() == spot.z()
            && Math.abs(entry.getValue().spot().y() - spot.y()) < 2);
    }
    public boolean hasRoom(Site site, UUID owner, int capacity, long now) {
        prune(now);
        return leases.entrySet().stream().filter(entry -> !entry.getKey().equals(owner) && entry.getValue().site().equals(site)).count() < capacity;
    }
    public boolean claim(UUID owner, Site site, Spot spot, int capacity, long now) {
        if (!hasRoom(site, owner, capacity, now) || !spotFree(site.dimension(), spot, owner, now)) return false;
        leases.put(owner, new Lease(site, spot, now + 200)); return true;
    }
    public boolean refresh(UUID owner, long now) {
        Lease lease = leases.get(owner);
        if (lease == null || lease.until() <= now) { leases.remove(owner); return false; }
        leases.put(owner, new Lease(lease.site(), lease.spot(), now + 200)); return true;
    }
    public boolean campfireNear(Site site, UUID except, int radius, long now) {
        prune(now);
        return leases.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(except) && entry.getValue().site().campfire()
            && entry.getValue().site().dimension().equals(site.dimension()) && distanceSquared(site, entry.getValue().site()) < (long)radius * radius);
    }
    private static long distanceSquared(Site a, Site b) {
        long x = (long)a.x() - b.x(), y = (long)a.y() - b.y(), z = (long)a.z() - b.z(); return x*x + y*y + z*z;
    }
    public void release(UUID owner) { leases.remove(owner); }
    public void clear() { leases.clear(); }
}
