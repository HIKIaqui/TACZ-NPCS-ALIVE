package mod.cdv.tnpc.campfire;

import java.util.*;

/** Persisted deadlines plus a bounded-work runtime queue; no block/chunk loading here. */
public final class CampfireClock {
    public record Due(long position, long deadline) {}
    private final Map<Long, Long> deadlines = new HashMap<>();
    private final Map<Long, Long> chunks = new HashMap<>();
    private final Map<Long, Set<Long>> byChunk = new HashMap<>();
    private final Map<Long, Long> queued = new HashMap<>();
    private final PriorityQueue<Due> queue = new PriorityQueue<>(Comparator.comparingLong(Due::deadline));

    public void restart(long pos, long chunk, long deadline) {
        forget(pos);
        deadlines.put(pos, deadline); chunks.put(pos, chunk);
        byChunk.computeIfAbsent(chunk, key -> new HashSet<>()).add(pos);
        enqueue(pos);
    }
    public boolean observe(long pos, long chunk, long now, int duration) {
        boolean created = !deadlines.containsKey(pos);
        if (created) restart(pos, chunk, now + duration); else enqueue(pos);
        return created;
    }
    public void enqueue(long pos) {
        Long deadline = deadlines.get(pos);
        if (deadline != null && !deadline.equals(queued.get(pos))) {
            queued.put(pos, deadline); queue.add(new Due(pos, deadline));
        }
    }
    public boolean forget(long pos) {
        Long chunk = chunks.remove(pos);
        if (chunk != null) {
            Set<Long> positions = byChunk.get(chunk);
            positions.remove(pos); if (positions.isEmpty()) byChunk.remove(chunk);
        }
        queued.remove(pos);
        return deadlines.remove(pos) != null;
    }
    public List<Due> pollDue(long now, int budget) {
        List<Due> due = new ArrayList<>();
        while (budget-- > 0 && !queue.isEmpty() && queue.peek().deadline() <= now) {
            Due entry = queue.poll();
            if (!Objects.equals(deadlines.get(entry.position()), entry.deadline())) continue;
            queued.remove(entry.position(), entry.deadline()); due.add(entry);
        }
        return due;
    }
    public Set<Long> positionsInChunk(long chunk) { return Set.copyOf(byChunk.getOrDefault(chunk, Set.of())); }
    public Map<Long, Long> snapshot() { return Map.copyOf(deadlines); }
    public boolean isEmpty() { return deadlines.isEmpty(); }
    public void clear() { deadlines.clear(); chunks.clear(); byChunk.clear(); queued.clear(); queue.clear(); }
}
