package mod.cdv.tnpc.reputation;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Immutable policy. Markers are thresholds, not caps on stored reputation. */
public final class ReputationConfig {
    public static final int LIMIT = 1_000_000_000;
    public record Settings(int hostileAt, int alliedAt, int tradeMinimum, int defaultFactionReputation,
                           Map<String, Integer> factionDefaults) {
        public int initial(String faction) { return factionDefaults.getOrDefault(faction, defaultFactionReputation); }
        public boolean hostile(long value) { return value <= hostileAt; }
        public boolean allied(long value) { return value >= alliedAt; }
    }
    public static final String DEFAULT_JSON = """
        {"hostileAt":-50,"alliedAt":50,"tradeMinimum":-49,
         "defaultFactionReputation":0,"factionDefaults":{}}
        """;
    private static volatile Settings current = parse(DEFAULT_JSON);
    private ReputationConfig() {}
    public static Settings settings() { return current; }
    public static boolean load(MinecraftServer server) {
        return loadFile(server.getServerDirectory().resolve("config/tacznpc_reputation.json"));
    }
    public static boolean loadFile(Path path) {
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) Files.writeString(path, DEFAULT_JSON);
            current = parse(Files.readString(path)); return true;
        } catch (Exception e) {
            LogUtils.getLogger().error("Could not load NPC reputation; keeping previous settings", e); return false;
        }
    }
    public static Settings parse(String json) {
        var o = JsonParser.parseString(json).getAsJsonObject();
        keys(o, Set.of("hostileAt", "alliedAt", "tradeMinimum", "defaultFactionReputation", "factionDefaults"));
        Map<String,Integer> defaults = new TreeMap<>();
        if (o.has("factionDefaults")) for (var e : o.getAsJsonObject("factionDefaults").entrySet()) {
            id(e.getKey()); defaults.put(e.getKey(), score(e.getValue()));
        }
        var result = new Settings(integer(o,"hostileAt",-50), integer(o,"alliedAt",50),
            integer(o,"tradeMinimum",-49), integer(o,"defaultFactionReputation",0), Map.copyOf(defaults));
        if (result.hostileAt() >= 0 || result.alliedAt() <= 0 || result.tradeMinimum() <= result.hostileAt())
            throw new IllegalArgumentException("Require hostileAt < 0 < alliedAt and tradeMinimum > hostileAt");
        return result;
    }
    /** Legacy config aliases only; all runtime identifiers use English. */
    public static String canonicalTier(String id) {
        return switch(id) {
            case "vilanizado" -> "villainized";
            case "hostil" -> "hostile";
            case "odiado" -> "hated";
            case "neutro" -> "neutral";
            case "amigavel" -> "friendly";
            case "aliado" -> "allied";
            case "idolizado" -> "idolized";
            default -> id;
        };
    }
    public static String tier(long value) {
        if (value <= -100) return "villainized";
        if (value <= -50) return "hostile";
        if (value <= -25) return "hated";
        if (value < 25) return "neutral";
        if (value < 50) return "friendly";
        if (value < 100) return "allied";
        return "idolized";
    }
    public static int score(JsonElement e) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Reputation must be an integer");
        int n = e.getAsBigDecimal().intValueExact();
        if (n < -LIMIT || n > LIMIT) throw new IllegalArgumentException("Reputation outside supported range");
        return n;
    }
    private static int integer(JsonObject o,String key,int fallback) { return o.has(key) ? score(o.get(key)) : fallback; }
    public static void id(String id) {
        if (!id.matches("[a-zA-Z0-9_.:/-]{1,160}")) throw new IllegalArgumentException("Invalid relation/faction ID: " + id);
    }
    public static void keys(JsonObject o,Set<String> allowed) {
        for (String key : o.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown reputation setting: " + key);
    }
}
