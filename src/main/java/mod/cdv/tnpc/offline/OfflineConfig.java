package mod.cdv.tnpc.offline;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Independent reloadable feature config; existing behavior files remain valid. */
public final class OfflineConfig {
    public record Action(boolean enabled, int cost, int weight) {}
    public record Profile(boolean enabled, Map<String, Action> actions) {}
    public record Settings(int ticksPerPoint, int maxActionsPerTick, int nearbyNpcLimit,
                           int populationRadius, Profile defaults, Map<String, Profile> loadouts) {}
    public static final String DEFAULT_JSON = """
        {
          "ticksPerPoint": 1200,
          "maxActionsPerTick": 16,
          "nearbyNpcLimit": 8,
          "populationRadius": 32,
          "offlineActions": {
            "enabled": true,
            "placeCampfire": { "enabled": true, "cost": 2, "weight": 1 },
            "rest": { "enabled": true, "cost": 1, "weight": 1 },
            "meetAllies": { "enabled": true, "cost": 5, "weight": 1 },
            "loot": { "enabled": true, "cost": 2, "weight": 1 },
            "share": { "enabled": true, "cost": 1, "weight": 1 }
          },
          "loadouts": {
            "villagers": { "offlineActions": { "share": { "weight": 2 } } },
            "illagers": { "offlineActions": { "loot": { "weight": 2 } } }
          }
        }
        """;
    private static volatile Settings current = parse(JsonParser.parseString(DEFAULT_JSON).getAsJsonObject());
    public static Settings settings() { return current; }
    public static Profile forLoadout(String id) { return current.loadouts().getOrDefault(id, current.defaults()); }
    public static boolean load(MinecraftServer server) {
        Path path = server.getServerDirectory().resolve("config/tacznpc_offline_actions.json");
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) Files.writeString(path, DEFAULT_JSON);
            Settings candidate = parse(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
            var tables = OfflineLoot.readDirectory(path.resolveSibling("tacznpc_loot"), server.registryAccess());
            OfflineLoot.install(tables);
            current = candidate;
            return true;
        } catch (Exception e) {
            LogUtils.getLogger().error("Could not load {}; keeping previous offlineActions settings", path, e);
            return false;
        }
    }
    static Settings parse(JsonObject root) {
        Profile builtin = new Profile(true, Map.of("placeCampfire", new Action(true, 2, 1),
            "rest", new Action(true, 1, 1), "meetAllies", new Action(true, 5, 1),
            "loot", new Action(true, 2, 1), "share", new Action(true, 1, 1)));
        Profile defaults = profile(object(root, "offlineActions"), builtin);
        Map<String, Profile> loadouts = new HashMap<>();
        for (var entry : object(root, "loadouts").entrySet())
            loadouts.put(entry.getKey(), profile(object(entry.getValue().getAsJsonObject(), "offlineActions"), defaults));
        return new Settings(integer(root, "ticksPerPoint", 1200, 1, 1728000),
            integer(root, "maxActionsPerTick", 16, 1, 128), integer(root, "nearbyNpcLimit", 8, 1, 64),
            integer(root, "populationRadius", 32, 8, 64), defaults, Map.copyOf(loadouts));
    }
    private static Profile profile(JsonObject root, Profile fallback) {
        for (String id : root.keySet())
            if (!id.equals("enabled") && !fallback.actions().containsKey(id))
                throw new IllegalArgumentException("Unknown offline action: " + id);
        Map<String, Action> actions = new LinkedHashMap<>();
        for (String id : List.of("placeCampfire", "rest", "meetAllies", "loot", "share")) {
            Action f = fallback.actions().get(id); JsonObject o = object(root, id);
            actions.put(id, new Action(bool(o, "enabled", f.enabled()), integer(o, "cost", f.cost(), 1, 1000000),
                integer(o, "weight", f.weight(), 1, 1000)));
        }
        // Legacy loot.table fields are deliberately ignored. Loadouts can never select the loot source.
        return new Profile(bool(root, "enabled", fallback.enabled()), Collections.unmodifiableMap(actions));
    }
    private static JsonObject object(JsonObject root, String key) {
        return root.has(key) ? root.getAsJsonObject(key) : new JsonObject();
    }
    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }
    private static int integer(JsonObject o, String key, int fallback, int min, int max) {
        if (!o.has(key)) return fallback;
        int value = o.get(key).getAsBigDecimal().intValueExact();
        if (value < min || value > max) throw new IllegalArgumentException("Invalid " + key);
        return value;
    }
}
