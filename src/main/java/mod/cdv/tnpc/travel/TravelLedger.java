package mod.cdv.tnpc.travel;

import java.util.*;
import java.io.UncheckedIOException;
import java.nio.file.*;
import mod.cdv.tnpc.travel.TravelOwnership.Phase;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One world-wide authority. Receipts remain to reject stale origin-chunk copies. */
public final class TravelLedger extends SavedData {

    public static final class Entry {
        public final UUID uuid;
        public final long epoch;
        public Phase phase;
        public String dimension;
        public CompoundTag npc;
        public long updatedAt;
        public boolean recall;
        public Entry(UUID uuid, long epoch, Phase phase, String dimension, CompoundTag npc, long updatedAt) {
            this.uuid = uuid; this.epoch = epoch; this.phase = phase; this.dimension = dimension;
            this.npc = npc.copy(); this.updatedAt = updatedAt;
        }
        CompoundTag save() {
            var t = new CompoundTag(); t.putUUID("uuid", uuid); t.putLong("epoch", epoch);
            t.putString("phase", phase.name()); t.putString("dimension", dimension);
            t.put("npc", npc.copy()); t.putLong("updatedAt", updatedAt); t.putBoolean("recall", recall); return t;
        }
    }
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    public static TravelLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(TravelLedger::new, TravelLedger::load), "tacznpcs_travel");
    }
    public Collection<Entry> entries() { return Collections.unmodifiableCollection(entries.values()); }
    public Entry get(UUID id) { return entries.get(id); }
    public void put(Entry e) { entries.put(e.uuid, e); NpcTravel.recordChanged(e.uuid); setDirty(); }
    void rollback(UUID id, Entry previous) {
        if (previous == null) entries.remove(id); else entries.put(id, previous);
        setDirty();
    }
    /** Write only this ledger; unlike SavedData.save(File,...), propagate IO failures.
     * The standard SavedData envelope remains readable by DimensionDataStorage. */
    public void flush(MinecraftServer server) {
        Path temporary = null;
        try {
            Path file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data/tacznpcs_travel.dat");
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "tacznpcs-travel-", ".tmp");
            CompoundTag root = new CompoundTag();
            root.put("data", save(new CompoundTag(), server.registryAccess()));
            NbtIo.writeCompressed(NbtUtils.addCurrentDataVersion(root), temporary);
            // Refuse the ownership change on filesystems without atomic replacement.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            setDirty(false);
        } catch (java.io.IOException failure) {
            throw new UncheckedIOException("Could not commit NPC travel ownership", failure);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (java.io.IOException ignored) { }
        }
    }
    private static TravelLedger load(CompoundTag root, HolderLookup.Provider lookup) {
        var ledger = new TravelLedger();
        for (var raw : root.getList("entries", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag)raw;
            // Corruption must fail loudly, never silently delete the only NPC snapshot.
            var e = new Entry(t.getUUID("uuid"), t.getLong("epoch"), Phase.valueOf(t.getString("phase")),
                t.getString("dimension"), t.getCompound("npc"), t.getLong("updatedAt"));
            e.recall = t.getBoolean("recall");
            if (e.epoch <= 0 || e.dimension.isBlank() || ledger.entries.put(e.uuid, e) != null)
                throw new IllegalStateException("Invalid or duplicate travel record " + e.uuid);
        }
        return ledger;
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        var list = new ListTag(); entries.values().forEach(e -> list.add(e.save()));
        root.putInt("version", 1); root.put("entries", list); return root;
    }
}
