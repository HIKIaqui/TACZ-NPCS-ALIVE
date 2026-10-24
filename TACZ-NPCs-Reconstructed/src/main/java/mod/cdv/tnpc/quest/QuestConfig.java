package mod.cdv.tnpc.quest;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class QuestConfig {
    public record Progression(int xpPerLevel, double healthPerLevel, double maxHealth) {
        public long level(long xp) { return Math.max(0, xp) / xpPerLevel; }
        public double bonus(long xp, double baseHealth) {
            return Math.min(Math.max(0, maxHealth - baseHealth), level(xp) * healthPerLevel);
        }
    }
    public record Settings(boolean enabled, int checkIntervalTicks, int assignmentRetryTicks,
        Progression progression, Map<String, QuestDefinition> quests, boolean needsStructures, int poiRadius) {}
    private static volatile Settings current = new Settings(true, 100, 200,
        new Progression(100, 1, 30), Map.of(), false, -1);
    private QuestConfig() {}
    public static Settings settings() { return current; }

    public static boolean load(MinecraftServer server) {
        Path config = server.getServerDirectory().resolve("config");
        try {
            Files.createDirectories(config);
            Path master = config.resolve("tacznpc_quests.json"), directory = config.resolve("tacznpc_quests");
            if (Files.notExists(master)) copyExample("config/tacznpc_quests.json", master);
            // An existing empty directory intentionally disables the default quest catalogue.
            if (Files.notExists(directory)) {
                Files.createDirectories(directory);
                for (String name : List.of("find_plains", "visit_village", "visit_meeting_point"))
                    copyExample("config/tacznpc_quests/" + name + ".json", directory.resolve(name + ".json"));
            }
            Settings candidate = read(master, directory);
            validateRegistries(candidate, server.registryAccess());
            current = candidate;
            LogUtils.getLogger().info("NPC quests loaded: {} definitions", candidate.quests().size());
            return true;
        } catch (Exception e) {
            LogUtils.getLogger().error("Could not load NPC quests; keeping previous quest settings", e);
            return false;
        }
    }
    private static void copyExample(String resource, Path destination) throws IOException {
        try (var input = QuestConfig.class.getResourceAsStream("/npc_examples/" + resource)) {
            if (input == null) throw new IOException("Missing quest example " + resource);
            Files.copy(input, destination);
        }
    }
    public static Settings read(Path master, Path directory) throws IOException {
        Map<String, JsonObject> definitions = new TreeMap<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).sorted().toList()) {
                String id = file.getFileName().toString().replaceFirst("\\.json$", "");
                try { definitions.put(id, JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()); }
                catch (Exception e) { throw new IllegalArgumentException("Invalid quest file " + file, e); }
            }
        }
        return parse(JsonParser.parseString(Files.readString(master)).getAsJsonObject(), definitions);
    }
    public static Settings parse(JsonObject root, Map<String, JsonObject> definitions) {
        keys(root, "settings", "enabled", "checkIntervalTicks", "assignmentRetryTicks", "progression");
        JsonObject p = object(root, "progression");
        keys(p, "progression", "xpPerLevel", "healthPerLevel", "maxHealth");
        Progression progression = new Progression(integer(p, "xpPerLevel", 100, 1, 1000000),
            number(p, "healthPerLevel", 1, 0, 100), number(p, "maxHealth", 30, 1, 1024));
        Map<String, QuestDefinition> quests = new TreeMap<>();
        boolean structures = false; int radius = -1; long totalWeight = 0;
        if (definitions.size() > 1024) throw new IllegalArgumentException("At most 1024 quest files are supported");
        for (var entry : definitions.entrySet()) {
            String id = entry.getKey();
            if (!id.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Invalid quest filename: " + id);
            QuestDefinition quest;
            try { quest = definition(id, entry.getValue()); }
            catch (Exception e) { throw new IllegalArgumentException("Invalid quest " + id + ": " + e.getMessage(), e); }
            quests.put(id, quest);
            if (!quest.enabled()) continue;
            totalWeight += quest.weight();
            structures |= quest.objective().type() == QuestDefinition.Type.STRUCTURE
                || !quest.acceptance().excludedStructures().isEmpty() || !quest.objective().exclusions().excludedStructures().isEmpty();
            if (quest.objective().type() == QuestDefinition.Type.POI || !quest.acceptance().excludedPoiTypes().isEmpty()
                || !quest.objective().exclusions().excludedPoiTypes().isEmpty()) radius = Math.max(radius, quest.objective().poiRadius());
        }
        if (totalWeight > Integer.MAX_VALUE) throw new IllegalArgumentException("Quest weights overflow");
        return new Settings(bool(root, "enabled", true), integer(root, "checkIntervalTicks", 100, 20, 12000),
            integer(root, "assignmentRetryTicks", 200, 20, 72000), progression,
            Collections.unmodifiableMap(quests), structures, radius);
    }
    private static QuestDefinition definition(String id, JsonObject root) {
        keys(root, id, "enabled", "weight", "xp", "loadouts", "excludedLoadouts", "dimensions", "acceptance", "objective", "dialogues");
        JsonObject a = object(root, "acceptance");
        keys(a, "acceptance", "requireOutsideObjective", "excludedBiomes", "excludedStructures", "excludedPoiTypes");
        JsonObject o = object(root, "objective");
        keys(o, "objective", "type", "targets", "poiRadius", "minTravelDistance", "excludedBiomes", "excludedStructures", "excludedPoiTypes");
        QuestDefinition.Type type = QuestDefinition.Type.valueOf(requiredString(o, "type").toUpperCase(Locale.ROOT));
        Set<String> targets = ids(o, "targets");
        if (targets.isEmpty()) throw new IllegalArgumentException("objective.targets cannot be empty");
        double minDistance = number(o, "minTravelDistance", 32, 0, 1000000);
        boolean outside = bool(a, "requireOutsideObjective", true);
        if (!outside && minDistance < 1) throw new IllegalArgumentException("Require outside objective or a minTravelDistance of at least 1");
        JsonObject d = object(root, "dialogues"); keys(d, "dialogues", "default", "loadouts");
        Map<String, QuestDefinition.Lines> overrides = new TreeMap<>();
        for (var e : object(d, "loadouts").entrySet()) overrides.put(e.getKey(), lines(e.getValue().getAsJsonObject()));
        return new QuestDefinition(id, bool(root, "enabled", true), integer(root, "weight", 1, 1, 1000000),
            integer(root, "xp", 25, 0, 1000000), strings(root, "loadouts"), strings(root, "excludedLoadouts"), ids(root, "dimensions"),
            outside, rules(a), new QuestDefinition.Objective(type, targets, integer(o, "poiRadius", 8, 1, 32), minDistance, rules(o)),
            new QuestDefinition.Dialogues(lines(object(d, "default")), Map.copyOf(overrides)));
    }
    private static QuestDefinition.LocationRules rules(JsonObject o) {
        return new QuestDefinition.LocationRules(ids(o, "excludedBiomes"), ids(o, "excludedStructures"), ids(o, "excludedPoiTypes"));
    }
    private static QuestDefinition.Lines lines(JsonObject o) {
        keys(o, "dialogue lines", "activity", "news");
        return new QuestDefinition.Lines(texts(o, "activity"), texts(o, "news"));
    }
    private static List<String> texts(JsonObject o, String key) {
        if (!o.has(key)) return List.of();
        JsonElement value = o.get(key);
        List<JsonElement> elements = value.isJsonArray() ? value.getAsJsonArray().asList() : List.of(value);
        if (elements.size() > 64) throw new IllegalArgumentException("Too many lines in " + key);
        return elements.stream().map(e -> {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must contain strings");
            String s = e.getAsString();
            if (s.isBlank() || s.length() > 4096) throw new IllegalArgumentException("Invalid text in " + key);
            return s;
        }).toList();
    }
    private static Set<String> strings(JsonObject o, String key) {
        if (!o.has(key)) return Set.of();
        if (!o.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement e : o.getAsJsonArray(key)) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isBlank())
                throw new IllegalArgumentException(key + " must contain nonempty strings");
            values.add(e.getAsString());
        }
        return Set.copyOf(values);
    }
    private static Set<String> ids(JsonObject o, String key) {
        Set<String> values = strings(o, key);
        for (String id : values) if (ResourceLocation.tryParse(id) == null || !id.contains(":"))
            throw new IllegalArgumentException("Invalid namespaced ID in " + key + ": " + id);
        return values;
    }
    private static String requiredString(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("Missing string " + key);
        return o.get(key).getAsString();
    }
    private static JsonObject object(JsonObject o, String key) { return o.has(key) ? o.getAsJsonObject(key) : new JsonObject(); }
    private static boolean bool(JsonObject o, String key, boolean fallback) {
        if (!o.has(key)) return fallback;
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException(key + " must be boolean");
        return o.get(key).getAsBoolean();
    }
    private static int integer(JsonObject o, String key, int fallback, int min, int max) {
        if (!o.has(key)) return fallback;
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException(key + " must be numeric");
        int n = o.get(key).getAsBigDecimal().intValueExact();
        if (n < min || n > max) throw new IllegalArgumentException("Out of range " + key);
        return n;
    }
    private static double number(JsonObject o, String key, double fallback, double min, double max) {
        if (!o.has(key)) return fallback;
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException(key + " must be numeric");
        double n = o.get(key).getAsDouble();
        if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException("Out of range " + key);
        return n;
    }
    private static void keys(JsonObject o, String label, String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String name : o.keySet()) if (!names.contains(name)) throw new IllegalArgumentException("Unknown field " + label + "." + name);
    }
    public static void validateRegistries(Settings settings, RegistryAccess registries) {
        for (QuestDefinition q : settings.quests().values()) {
            // Disabled examples may refer to optional modded content.
            if (!q.enabled()) continue;
            for (String id : q.dimensions()) requireId(registries.registryOrThrow(Registries.LEVEL_STEM), id, q.id());
            var targetRegistry = switch (q.objective().type()) {
                case BIOME -> registries.registryOrThrow(Registries.BIOME);
                case STRUCTURE -> registries.registryOrThrow(Registries.STRUCTURE);
                case POI -> registries.registryOrThrow(Registries.POINT_OF_INTEREST_TYPE);
            };
            for (String id : q.objective().targets()) requireId(targetRegistry, id, q.id());
            for (var rules : List.of(q.acceptance(), q.objective().exclusions())) {
                for (String id : rules.excludedBiomes()) requireId(registries.registryOrThrow(Registries.BIOME), id, q.id());
                for (String id : rules.excludedStructures()) requireId(registries.registryOrThrow(Registries.STRUCTURE), id, q.id());
                for (String id : rules.excludedPoiTypes()) requireId(registries.registryOrThrow(Registries.POINT_OF_INTEREST_TYPE), id, q.id());
            }
        }
    }
    private static void requireId(net.minecraft.core.Registry<?> registry, String id, String quest) {
        if (!registry.containsKey(ResourceLocation.parse(id))) throw new IllegalArgumentException("Quest " + quest + ": unknown registry ID " + id);
    }
}
