package mod.cdv.tnpc.reputation;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-wide, server-only relationships. NPC travel never needs to copy this data. */
public final class ReputationData extends SavedData {
    private final Map<UUID, Map<String,Integer>> factions = new HashMap<>();
    private final Map<UUID, Map<UUID,Integer>> personal = new HashMap<>();
    public static ReputationData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ReputationData::new, ReputationData::load), "tacznpcs_reputation");
    }
    public int faction(UUID player,String faction) {
        return factions.getOrDefault(player, Map.of()).getOrDefault(faction, ReputationConfig.settings().initial(faction));
    }
    public int personal(UUID player,UUID npc) { return personal.getOrDefault(player,Map.of()).getOrDefault(npc,0); }
    public long effective(UUID player,String faction,UUID npc) { return (long)faction(player,faction) + personal(player,npc); }
    public void faction(UUID player,String faction,int value) {
        ReputationConfig.id(faction); check(value);
        factions.computeIfAbsent(player,k -> new HashMap<>()).put(faction,value); setDirty();
    }
    public void personal(UUID player,UUID npc,int value) {
        check(value);
        if (value == 0) {
            var values = personal.get(player);
            if (values != null) { values.remove(npc); if (values.isEmpty()) personal.remove(player); }
        } else personal.computeIfAbsent(player,k -> new HashMap<>()).put(npc,value);
        setDirty();
    }
    public void resetFaction(UUID player,String faction) {
        var values = factions.get(player);
        if (values != null) { values.remove(faction); if (values.isEmpty()) factions.remove(player); setDirty(); }
    }
    public static int add(int value,int delta) { return (int)Math.clamp((long)value + delta,-ReputationConfig.LIMIT,ReputationConfig.LIMIT); }
    private static void check(int n) { if (n < -ReputationConfig.LIMIT || n > ReputationConfig.LIMIT) throw new IllegalArgumentException("Reputation outside supported range"); }
    public static ReputationData load(CompoundTag root,HolderLookup.Provider lookup) {
        if (root.getInt("version") != 1) throw new IllegalStateException("Unsupported NPC reputation data version");
        var result = new ReputationData();
        for (var raw : root.getList("players", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag)raw; UUID player = t.getUUID("player");
            if (result.factions.containsKey(player) || result.personal.containsKey(player)) throw new IllegalStateException("Duplicate reputation player");
            var f = new HashMap<String,Integer>(); var p = new HashMap<UUID,Integer>();
            for (var entry : t.getList("factions",Tag.TAG_COMPOUND)) {
                var e = (CompoundTag)entry; String id = e.getString("id"); int value = e.getInt("value");
                ReputationConfig.id(id); check(value);
                if (f.put(id,value) != null) throw new IllegalStateException("Duplicate reputation faction");
            }
            for (var entry : t.getList("personal",Tag.TAG_COMPOUND)) {
                var e = (CompoundTag)entry; int value = e.getInt("value"); check(value);
                if (p.put(e.getUUID("npc"),value) != null) throw new IllegalStateException("Duplicate personal reputation");
            }
            result.factions.put(player,f); result.personal.put(player,p);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup) {
        root.putInt("version",1); var list = new ListTag();
        Set<UUID> players = new HashSet<>(factions.keySet()); players.addAll(personal.keySet());
        for (UUID player : players) {
            var t = new CompoundTag(); t.putUUID("player",player); var f = new ListTag(); var p = new ListTag();
            factions.getOrDefault(player,Map.of()).forEach((id,value) -> { var e = new CompoundTag(); e.putString("id",id); e.putInt("value",value); f.add(e); });
            personal.getOrDefault(player,Map.of()).forEach((id,value) -> { var e = new CompoundTag(); e.putUUID("npc",id); e.putInt("value",value); p.add(e); });
            t.put("factions",f); t.put("personal",p); list.add(t);
        }
        root.put("players",list); return root;
    }
}
