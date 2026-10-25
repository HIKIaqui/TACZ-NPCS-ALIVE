package mod.cdv.tnpc.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/** Reloadable defaults and optional overrides keyed by the existing loadout ID. */
public final class NpcBehaviorConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_JSON = """
        {
          "rest": {
            "enabled": true,
            "poiTypes": ["minecraft:meeting"],
            "searchRadius": 24,
            "searchIntervalTicks": 100,
            "chance": 0.5,
            "minDurationTicks": 200,
            "maxDurationTicks": 600,
            "minCooldownTicks": 600,
            "maxCooldownTicks": 1800,
            "travelTimeoutTicks": 200,
            "speed": 1.0,
            "socialRadius": 6.0
          },
          "perception": {
            "enabled": true,
            "calmRange": 16.0,
            "restingRange": 6.0,
            "alertRange": 48.0,
            "alertDurationTicks": 600,
            "allyAlertRadius": 12.0
          },
          "damageEscape": {
            "enabled": true,
            "reactToAllies": true,
            "durationTicks": 100,
            "searchRadius": 12,
            "minDistance": 4.0,
            "speed": 1.3,
            "repathIntervalTicks": 20
          },
          "campfire": {
            "useAsRestPoi": true,
            "canCreate": true,
            "canLight": true,
            "creationCooldownTicks": 2400,
            "buildDurationTicks": 60,
            "igniteDurationTicks": 40,
            "requireSky": true
          },
          "campfires": {
            "CampfiresExtinguishAfterTime": true,
            "CampfiresTimeToExtinguish": 6000,
            "maxResters": 4,
            "minSpacing": 12
          },
          "combat": %s,
          "loadouts": {}
        }
        """.formatted(CombatSettings.DEFAULT_JSON);

    public record RestSettings(boolean enabled, Set<ResourceLocation> poiTypes, int searchRadius,
        int searchIntervalTicks, double chance, int minDurationTicks, int maxDurationTicks,
        int minCooldownTicks, int maxCooldownTicks, int travelTimeoutTicks, double speed,
        double socialRadius) {}

    public record PerceptionSettings(boolean enabled, double calmRange, double restingRange,
        double alertRange, int alertDurationTicks, double allyAlertRadius) {}

    public record CampfireSettings(boolean useAsRestPoi, boolean canCreate, boolean canLight,
        int creationCooldownTicks, int buildDurationTicks, int igniteDurationTicks, boolean requireSky) {}
    public record CampfireWorldSettings(boolean extinguishAfterTime, int timeToExtinguish, int maxResters, int minSpacing) {}

    public record DamageEscapeSettings(boolean enabled, boolean reactToAllies, int durationTicks,
        int searchRadius, double minDistance, double speed, int repathIntervalTicks) {}
    private static final DamageEscapeSettings BUILTIN_ESCAPE = new DamageEscapeSettings(true, true, 100, 12, 4, 1.3, 20);

    private record Snapshot(RestSettings defaults, Map<String, RestSettings> loadouts,
        PerceptionSettings perception, Map<String, PerceptionSettings> perceptionLoadouts,
        CampfireSettings campfire, Map<String, CampfireSettings> campfireLoadouts, CampfireWorldSettings campfires, DamageEscapeSettings escape, Map<String, DamageEscapeSettings> escapeLoadouts,
        CombatSettings.Profile combat, Map<String, CombatSettings.Profile> combatLoadouts) {}
    private static final RestSettings BUILTIN = new RestSettings(true,
        Set.of(ResourceLocation.withDefaultNamespace("meeting")), 24, 100, 0.5,
        200, 600, 600, 1800, 200, 1.0, 6.0);
    private static final PerceptionSettings BUILTIN_PERCEPTION = new PerceptionSettings(true, 16, 6, 48, 600, 12);
    private static final CampfireSettings BUILTIN_CAMPFIRE = new CampfireSettings(true, true, true, 2400, 60, 40, true);
    private static final CampfireWorldSettings BUILTIN_CAMPFIRES = new CampfireWorldSettings(true, 6000, 4, 12);
    private static volatile Snapshot current = new Snapshot(BUILTIN, Map.of(), BUILTIN_PERCEPTION, Map.of(), BUILTIN_CAMPFIRE, Map.of(), BUILTIN_CAMPFIRES, BUILTIN_ESCAPE, Map.of(), CombatSettings.DEFAULTS, Map.of());

    private NpcBehaviorConfig() {}

    public static RestSettings restFor(String loadout) {
        Snapshot snapshot = current;
        return snapshot.loadouts().getOrDefault(loadout, snapshot.defaults());
    }

    public static PerceptionSettings perceptionFor(String loadout) {
        Snapshot snapshot = current;
        return snapshot.perceptionLoadouts().getOrDefault(loadout, snapshot.perception());
    }

    public static CampfireSettings campfireFor(String loadout) {
        Snapshot snapshot = current;
        return snapshot.campfireLoadouts().getOrDefault(loadout, snapshot.campfire());
    }
    public static DamageEscapeSettings damageEscapeFor(String loadout) {
        Snapshot snapshot = current;
        return snapshot.escapeLoadouts().getOrDefault(loadout, snapshot.escape());
    }
    public static CampfireWorldSettings campfires() { return current.campfires(); }
    public static CombatSettings.Profile combatFor(String loadout) {
        Snapshot snapshot = current;
        return snapshot.combatLoadouts().getOrDefault(loadout, snapshot.combat());
    }

    /** A malformed reload keeps the last successfully loaded settings. */
    public static boolean load(MinecraftServer server) {
        return loadFile(server.getServerDirectory().resolve("config/tacznpc_behavior.json"));
    }

    public static boolean loadFile(Path path) {
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) Files.writeString(path, DEFAULT_JSON);
            JsonObject root = new GsonBuilder().create().fromJson(Files.readString(path), JsonObject.class);
            if (root == null) throw new IllegalArgumentException("Root must be an object");
            RestSettings defaults = readRest(root.has("rest") ? root.getAsJsonObject("rest") : new JsonObject(), BUILTIN);
            PerceptionSettings perception = readPerception(root.has("perception") ? root.getAsJsonObject("perception") : new JsonObject(), BUILTIN_PERCEPTION);
            CampfireSettings campfire = readCampfire(root.has("campfire") ? root.getAsJsonObject("campfire") : new JsonObject(), BUILTIN_CAMPFIRE);
            CampfireWorldSettings campfires = readCampfireWorld(root.has("campfires") ? root.getAsJsonObject("campfires") : new JsonObject());
            DamageEscapeSettings escape = readEscape(root.has("damageEscape") ? root.getAsJsonObject("damageEscape") : new JsonObject(), BUILTIN_ESCAPE);
            CombatSettings.Profile combat = CombatSettings.parse(root.has("combat") ? root.getAsJsonObject("combat") : new JsonObject(), CombatSettings.DEFAULTS);
            Map<String, CombatSettings.Profile> combatLoadouts = new HashMap<>();
            Map<String, DamageEscapeSettings> escapeLoadouts = new HashMap<>();
            Map<String, CampfireSettings> campfireLoadouts = new HashMap<>();
            Map<String, RestSettings> loadouts = new HashMap<>();
            Map<String, PerceptionSettings> perceptionLoadouts = new HashMap<>();
            if (root.has("loadouts")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("loadouts").entrySet()) {
                    JsonObject profile = entry.getValue().getAsJsonObject();
                    combatLoadouts.put(entry.getKey(), CombatSettings.parse(profile.has("combat") ? profile.getAsJsonObject("combat") : new JsonObject(), combat));
                    escapeLoadouts.put(entry.getKey(), readEscape(profile.has("damageEscape") ? profile.getAsJsonObject("damageEscape") : new JsonObject(), escape));
                    loadouts.put(entry.getKey(), readRest(profile.has("rest") ? profile.getAsJsonObject("rest") : new JsonObject(), defaults));
                    campfireLoadouts.put(entry.getKey(), readCampfire(profile.has("campfire") ? profile.getAsJsonObject("campfire") : new JsonObject(), campfire));
                    perceptionLoadouts.put(entry.getKey(), readPerception(profile.has("perception") ? profile.getAsJsonObject("perception") : new JsonObject(), perception));
                }
            }
            current = new Snapshot(defaults, Map.copyOf(loadouts), perception, Map.copyOf(perceptionLoadouts), campfire, Map.copyOf(campfireLoadouts), campfires, escape, Map.copyOf(escapeLoadouts), combat, Map.copyOf(combatLoadouts));
            LOGGER.info("NPC behavior loaded from {}", path);
            return true;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not load {}. Keeping previous NPC behavior settings.", path, exception);
            return false;
        }
    }

    private static DamageEscapeSettings readEscape(JsonObject o, DamageEscapeSettings fallback) {
        DamageEscapeSettings settings = new DamageEscapeSettings(bool(o, "enabled", fallback.enabled()),
            bool(o, "reactToAllies", fallback.reactToAllies()), integer(o, "durationTicks", fallback.durationTicks()),
            integer(o, "searchRadius", fallback.searchRadius()), number(o, "minDistance", fallback.minDistance()),
            number(o, "speed", fallback.speed()), integer(o, "repathIntervalTicks", fallback.repathIntervalTicks()));
        if (settings.durationTicks() < 20 || settings.durationTicks() > 1200
            || settings.searchRadius() < 4 || settings.searchRadius() > 32
            || !Double.isFinite(settings.minDistance()) || settings.minDistance() < 1 || settings.minDistance() > settings.searchRadius()
            || !Double.isFinite(settings.speed()) || settings.speed() <= 0 || settings.speed() > 4
            || settings.repathIntervalTicks() < 5 || settings.repathIntervalTicks() > 100)
            throw new IllegalArgumentException("Invalid damage escape settings: " + settings);
        return settings;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }
    private static CampfireSettings readCampfire(JsonObject o, CampfireSettings fallback) {
        CampfireSettings settings = new CampfireSettings(bool(o, "useAsRestPoi", fallback.useAsRestPoi()),
            bool(o, "canCreate", fallback.canCreate()), bool(o, "canLight", fallback.canLight()),
            integer(o, "creationCooldownTicks", fallback.creationCooldownTicks()),
            integer(o, "buildDurationTicks", fallback.buildDurationTicks()),
            integer(o, "igniteDurationTicks", fallback.igniteDurationTicks()), bool(o, "requireSky", fallback.requireSky()));
        if (settings.creationCooldownTicks() < 20 || settings.creationCooldownTicks() > 1728000
            || settings.buildDurationTicks() < 1 || settings.buildDurationTicks() > 1200
            || settings.igniteDurationTicks() < 1 || settings.igniteDurationTicks() > 1200)
            throw new IllegalArgumentException("Invalid campfire capability settings: " + settings);
        return settings;
    }
    private static CampfireWorldSettings readCampfireWorld(JsonObject o) {
        CampfireWorldSettings settings = new CampfireWorldSettings(
            bool(o, "CampfiresExtinguishAfterTime", BUILTIN_CAMPFIRES.extinguishAfterTime()),
            integer(o, "CampfiresTimeToExtinguish", BUILTIN_CAMPFIRES.timeToExtinguish()),
            integer(o, "maxResters", BUILTIN_CAMPFIRES.maxResters()), integer(o, "minSpacing", BUILTIN_CAMPFIRES.minSpacing()));
        if (settings.timeToExtinguish() < 20 || settings.timeToExtinguish() > 1728000
            || settings.maxResters() < 1 || settings.maxResters() > 12 || settings.minSpacing() < 4 || settings.minSpacing() > 64)
            throw new IllegalArgumentException("Invalid global campfire settings: " + settings);
        return settings;
    }

    private static PerceptionSettings readPerception(JsonObject o, PerceptionSettings fallback) {
        PerceptionSettings settings = new PerceptionSettings(
            o.has("enabled") ? o.get("enabled").getAsBoolean() : fallback.enabled(),
            number(o, "calmRange", fallback.calmRange()), number(o, "restingRange", fallback.restingRange()),
            number(o, "alertRange", fallback.alertRange()), integer(o, "alertDurationTicks", fallback.alertDurationTicks()),
            number(o, "allyAlertRadius", fallback.allyAlertRadius()));
        if (!Double.isFinite(settings.restingRange()) || settings.restingRange() < 1
            || !Double.isFinite(settings.calmRange()) || settings.calmRange() < settings.restingRange()
            || !Double.isFinite(settings.alertRange()) || settings.alertRange() < settings.calmRange() || settings.alertRange() > 128
            || settings.alertDurationTicks() < 20 || settings.alertDurationTicks() > 72000
            || !Double.isFinite(settings.allyAlertRadius()) || settings.allyAlertRadius() < 0 || settings.allyAlertRadius() > 48)
            throw new IllegalArgumentException("Invalid perception settings: " + settings);
        return settings;
    }

    private static RestSettings readRest(JsonObject o, RestSettings fallback) {
        Set<ResourceLocation> poiTypes = fallback.poiTypes();
        if (o.has("poiTypes")) {
            JsonArray ids = o.getAsJsonArray("poiTypes");
            poiTypes = ids.asList().stream().map(e -> ResourceLocation.parse(e.getAsString())).collect(Collectors.toUnmodifiableSet());
        }
        RestSettings settings = new RestSettings(
            o.has("enabled") ? o.get("enabled").getAsBoolean() : fallback.enabled(), poiTypes,
            integer(o, "searchRadius", fallback.searchRadius()),
            integer(o, "searchIntervalTicks", fallback.searchIntervalTicks()),
            number(o, "chance", fallback.chance()),
            integer(o, "minDurationTicks", fallback.minDurationTicks()),
            integer(o, "maxDurationTicks", fallback.maxDurationTicks()),
            integer(o, "minCooldownTicks", fallback.minCooldownTicks()),
            integer(o, "maxCooldownTicks", fallback.maxCooldownTicks()),
            integer(o, "travelTimeoutTicks", fallback.travelTimeoutTicks()),
            number(o, "speed", fallback.speed()),
            number(o, "socialRadius", fallback.socialRadius()));
        if (settings.searchRadius() < 1 || settings.searchRadius() > 64
            || settings.searchIntervalTicks() < 20 || settings.searchIntervalTicks() > 72000
            || !Double.isFinite(settings.chance()) || settings.chance() < 0 || settings.chance() > 1
            || settings.minDurationTicks() < 1 || settings.maxDurationTicks() < settings.minDurationTicks() || settings.maxDurationTicks() > 72000
            || settings.minCooldownTicks() < 20 || settings.maxCooldownTicks() < settings.minCooldownTicks() || settings.maxCooldownTicks() > 72000
            || settings.travelTimeoutTicks() < 20 || settings.travelTimeoutTicks() > 72000
            || !Double.isFinite(settings.speed()) || settings.speed() <= 0 || settings.speed() > 4
            || !Double.isFinite(settings.socialRadius()) || settings.socialRadius() < 0 || settings.socialRadius() > 24) {
            throw new IllegalArgumentException("Invalid rest settings: " + settings);
        }
        return settings;
    }

    private static int integer(JsonObject o, String key, int fallback) {
        if (!o.has(key)) return fallback;
        // Reject fractions instead of silently truncating a typo.
        return o.get(key).getAsBigDecimal().intValueExact();
    }

    private static double number(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }
}
