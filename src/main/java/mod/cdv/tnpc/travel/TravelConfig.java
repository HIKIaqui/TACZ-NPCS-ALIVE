package mod.cdv.tnpc.travel;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import net.minecraft.server.MinecraftServer;

public final class TravelConfig {
    public record Settings(boolean enabled, double blocksPerPoint, int playerExclusionRadius,
                           int intervalTicks, int recordsPerPass, int sitesPerRecord, int arrivalCooldownTicks) {}
    public static final String DEFAULT_JSON = """
        {
          "enabled": false,
          "blocksPerPoint": 32.0,
          "playerExclusionRadius": 64,
          "intervalTicks": 20,
          "recordsPerPass": 4,
          "sitesPerRecord": 8,
          "arrivalCooldownTicks": 1200
        }
        """;
    private static Settings current = parse(DEFAULT_JSON);
    public static Settings settings() { return current; }
    public static boolean load(MinecraftServer server) {
        var path = server.getServerDirectory().resolve("config/tacznpc_travel.json");
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) Files.writeString(path, DEFAULT_JSON);
            current = parse(Files.readString(path)); return true;
        } catch (Exception e) {
            LogUtils.getLogger().error("Could not load {}; retaining previous travel settings", path, e);
            return false;
        }
    }
    public static Settings parse(String json) {
        var o = JsonParser.parseString(json).getAsJsonObject();
        for (String key : o.keySet()) if (!java.util.Set.of("enabled", "blocksPerPoint", "playerExclusionRadius",
            "intervalTicks", "recordsPerPass", "sitesPerRecord", "arrivalCooldownTicks").contains(key))
            throw new IllegalArgumentException("Unknown travel option: " + key);
        double speed = o.has("blocksPerPoint") ? o.get("blocksPerPoint").getAsDouble() : 32;
        if (!Double.isFinite(speed) || speed <= 0 || speed > 4096) throw new IllegalArgumentException("Invalid blocksPerPoint");
        return new Settings(o.has("enabled") && o.get("enabled").getAsBoolean(), speed,
            integer(o, "playerExclusionRadius", 64, 32, 256), integer(o, "intervalTicks", 20, 1, 1200),
            integer(o, "recordsPerPass", 4, 1, 64), integer(o, "sitesPerRecord", 8, 1, 64),
            integer(o, "arrivalCooldownTicks", 1200, 20, 1728000));
    }
    private static int integer(com.google.gson.JsonObject o, String key, int fallback, int min, int max) {
        int n = o.has(key) ? o.get(key).getAsBigDecimal().intValueExact() : fallback;
        if (n < min || n > max) throw new IllegalArgumentException("Invalid " + key);
        return n;
    }
}
