package mod.cdv.tnpc.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** NPC-owned state; independent of ticking entities so travel can reuse it later. */
public final class NpcQuestState {
    public record Active(String id, long serial, String dimension, BlockPos origin, long startedAt) {}
    private long xp, completedCount, nextSerial, nextAssignmentAt;
    private Active active;
    private String lastCompleted = "";
    private QuestConfig.Settings appliedSettings;
    public long xp() { return xp; }
    public long completedCount() { return completedCount; }
    public Active active() { return active; }
    public String lastCompleted() { return lastCompleted; }
    public long nextAssignmentAt() { return nextAssignmentAt; }
    public QuestConfig.Settings appliedSettings() { return appliedSettings; }
    public void appliedSettings(QuestConfig.Settings settings) { appliedSettings = settings; }
    public void addXp(long amount) {
        if (amount < 0) throw new IllegalArgumentException("XP cannot be negative");
        xp = amount > Long.MAX_VALUE - xp ? Long.MAX_VALUE : xp + amount;
    }
    public boolean assign(String id, QuestLocation.Snapshot place, long now) {
        if (nextSerial == Long.MAX_VALUE) return false;
        active = new Active(id, ++nextSerial, place.dimension(), place.position().immutable(), Math.max(0, now));
        return true;
    }
    public void clear(long retryAt) { active = null; nextAssignmentAt = Math.max(0, retryAt); }
    public boolean complete(long expectedSerial, int reward) {
        if (reward < 0) throw new IllegalArgumentException("Reward cannot be negative");
        if (active == null || active.serial() != expectedSerial) return false;
        String id = active.id(); active = null; // Claim before granting: one reward per assignment.
        addXp(reward);
        if (completedCount < Long.MAX_VALUE) completedCount++;
        lastCompleted = id; nextAssignmentAt = 0;
        return true;
    }
    public void save(CompoundTag root) {
        CompoundTag data = new CompoundTag(); data.putInt("version", 1);
        data.putLong("xp", xp); data.putLong("completed", completedCount); data.putLong("nextSerial", nextSerial);
        data.putLong("nextAssignmentAt", nextAssignmentAt); data.putString("lastCompleted", lastCompleted);
        if (active != null) {
            CompoundTag a = new CompoundTag(); a.putString("id", active.id()); a.putLong("serial", active.serial());
            a.putString("dimension", active.dimension()); a.putLong("origin", active.origin().asLong());
            a.putLong("startedAt", active.startedAt()); data.put("active", a);
        }
        root.put("tnpcQuests", data);
    }
    public void load(CompoundTag root) {
        CompoundTag data = root.getCompound("tnpcQuests");
        xp = Math.max(0, data.getLong("xp")); completedCount = Math.max(0, data.getLong("completed"));
        nextSerial = Math.max(0, data.getLong("nextSerial")); nextAssignmentAt = Math.max(0, data.getLong("nextAssignmentAt"));
        lastCompleted = data.getString("lastCompleted"); active = null; appliedSettings = null;
        CompoundTag a = data.getCompound("active");
        if (!a.getString("id").isBlank() && a.getLong("serial") > 0 && !a.getString("dimension").isBlank()) {
            active = new Active(a.getString("id"), a.getLong("serial"), a.getString("dimension"),
                BlockPos.of(a.getLong("origin")), Math.max(0, a.getLong("startedAt")));
            nextSerial = Math.max(nextSerial, active.serial());
        }
    }
}
