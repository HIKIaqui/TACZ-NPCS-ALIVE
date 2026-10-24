package mod.cdv.tnpc.offline;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Cumulative credit reaches unloaded NPCs without loading their chunks. */
public final class OfflineGrants extends SavedData {
    private long total;
    public static OfflineGrants get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(OfflineGrants::new, OfflineGrants::load), "tacznpcs_offline_grants");
    }
    private static OfflineGrants load(CompoundTag tag, HolderLookup.Provider lookup) {
        OfflineGrants data = new OfflineGrants(); data.total = Math.max(0, tag.getLong("total")); return data;
    }
    public long total() { return total; }
    public void add(long ticks) { total = Math.addExact(total, ticks); setDirty(); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putLong("total", total); return tag;
    }
}
