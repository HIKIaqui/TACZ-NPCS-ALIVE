package mod.cdv.tnpc.data;

import com.google.gson.*;
import java.util.*;
import java.util.function.IntUnaryOperator;

/** Pure configuration; loadout common fields override inherited weapon fields independently. */
public final class CombatSettings {
    public record Range(int min, int max) {
        public int sample(IntUnaryOperator random) { return min == max ? min : min + random.applyAsInt(max - min + 1); }
    }
    public record Point(double distance, double spreadMultiplier) {}
    public record Weapon(Range shotIntervalTicks, Range burstShots, Range burstPauseTicks) {}
    public record Profile(Range reactionTimeTicks, int targetMemoryTicks, double aimDistance,
                          double baseAimErrorDegrees, List<Point> accuracyByDistance,
                          Weapon defaults, Map<String, Weapon> weapons) {
        public Weapon weapon(String type) { return weapons.getOrDefault(type, defaults); }
    }
    private static final Set<String> TYPES = Set.of("default", "pistol", "rifle", "sniper", "shotgun", "smg", "mg", "rpg");
    private static final Set<String> WEAPON_KEYS = Set.of("shotIntervalTicks", "burstShots", "burstPauseTicks");
    public static final String DEFAULT_JSON = """
        {"reactionTimeTicks":{"min":10,"max":24},"targetMemoryTicks":40,"aimDistance":10,
         "baseAimErrorDegrees":0.6,
         "accuracyByDistance":[{"distance":0,"spreadMultiplier":1},{"distance":15,"spreadMultiplier":1.4},
                              {"distance":35,"spreadMultiplier":2.5},{"distance":60,"spreadMultiplier":4}],
         "shotIntervalTicks":{"min":2,"max":4},"burstShots":{"min":1,"max":2},
         "burstPauseTicks":{"min":18,"max":36},
         "weapons":{
           "pistol":{"shotIntervalTicks":{"min":4,"max":8},"burstPauseTicks":{"min":16,"max":32}},
           "smg":{"shotIntervalTicks":{"min":2,"max":4},"burstShots":{"min":1,"max":3},"burstPauseTicks":{"min":20,"max":36}},
           "mg":{"shotIntervalTicks":{"min":2,"max":4},"burstShots":{"min":1,"max":3},"burstPauseTicks":{"min":24,"max":44}},
           "sniper":{"shotIntervalTicks":{"min":0,"max":0},"burstShots":{"min":1,"max":1},"burstPauseTicks":{"min":40,"max":70}},
           "shotgun":{"burstShots":{"min":1,"max":1},"burstPauseTicks":{"min":28,"max":48}},
           "rpg":{"burstShots":{"min":1,"max":1},"burstPauseTicks":{"min":50,"max":90}}
         }}
        """;
    private static final Weapon SEED_WEAPON = new Weapon(new Range(2, 4), new Range(1, 2), new Range(18, 36));
    private static final Profile SEED = new Profile(new Range(10, 24), 40, 10, .6,
        List.of(new Point(0, 1), new Point(15, 1.4), new Point(35, 2.5), new Point(60, 4)), SEED_WEAPON, Map.of());
    public static final Profile DEFAULTS = parse(JsonParser.parseString(DEFAULT_JSON).getAsJsonObject(), SEED);
    private CombatSettings() {}
    public static Profile parse(JsonObject o, Profile fallback) {
        keys(o, Set.of("reactionTimeTicks", "targetMemoryTicks", "aimDistance", "baseAimErrorDegrees",
            "accuracyByDistance", "shotIntervalTicks", "burstShots", "burstPauseTicks", "weapons"));
        Range reaction = range(o, "reactionTimeTicks", fallback.reactionTimeTicks(), 0, 1200);
        int memory = o.has("targetMemoryTicks") ? o.get("targetMemoryTicks").getAsBigDecimal().intValueExact() : fallback.targetMemoryTicks();
        if (memory < 0 || memory > 1200) throw new IllegalArgumentException("Invalid targetMemoryTicks");
        double aim = number(o, "aimDistance", fallback.aimDistance(), 0, 256);
        double error = number(o, "baseAimErrorDegrees", fallback.baseAimErrorDegrees(), 0, 5);
        List<Point> points = fallback.accuracyByDistance();
        if (o.has("accuracyByDistance")) {
            var array = o.getAsJsonArray("accuracyByDistance");
            if (array.isEmpty() || array.size() > 32) throw new IllegalArgumentException("Invalid accuracy curve size");
            List<Point> candidate = new ArrayList<>(); double previous = -1;
            for (var item : array) {
                var p = item.getAsJsonObject(); keys(p, Set.of("distance", "spreadMultiplier"));
                if (!p.has("distance") || !p.has("spreadMultiplier")) throw new IllegalArgumentException("Incomplete accuracy point");
                double distance = number(p, "distance", 0, 0, 4096);
                if (distance <= previous || (candidate.isEmpty() && distance != 0)) throw new IllegalArgumentException("Accuracy distances must start at zero and increase");
                candidate.add(new Point(distance, number(p, "spreadMultiplier", 1, 0, 20))); previous = distance;
            }
            points = List.copyOf(candidate);
        }
        for (var p : points) if (error * p.spreadMultiplier() > 45) throw new IllegalArgumentException("NPC aim error exceeds 45 degrees");
        Weapon defaults = weapon(o, fallback.defaults(), false);
        JsonObject overrides = o.has("weapons") ? o.getAsJsonObject("weapons") : new JsonObject(); keys(overrides, TYPES);
        Map<String, Weapon> weapons = new HashMap<>();
        for (String type : TYPES) {
            Weapon inherited = weapon(o, fallback.weapon(type), false);
            weapons.put(type, overrides.has(type) ? weapon(overrides.getAsJsonObject(type), inherited, true) : inherited);
        }
        return new Profile(reaction, memory, aim, error, points, defaults, Map.copyOf(weapons));
    }
    private static Weapon weapon(JsonObject o, Weapon fallback, boolean strict) {
        if (strict) keys(o, WEAPON_KEYS);
        return new Weapon(range(o, "shotIntervalTicks", fallback.shotIntervalTicks(), 0, 1200),
            range(o, "burstShots", fallback.burstShots(), 1, 128), range(o, "burstPauseTicks", fallback.burstPauseTicks(), 0, 72000));
    }
    private static Range range(JsonObject o, String key, Range fallback, int lower, int upper) {
        if (!o.has(key)) return fallback;
        var r = o.getAsJsonObject(key); keys(r, Set.of("min", "max"));
        int min = r.has("min") ? r.get("min").getAsBigDecimal().intValueExact() : fallback.min();
        int max = r.has("max") ? r.get("max").getAsBigDecimal().intValueExact() : fallback.max();
        if (min < lower || max < min || max > upper) throw new IllegalArgumentException("Invalid " + key);
        return new Range(min, max);
    }
    private static double number(JsonObject o, String key, double fallback, double min, double max) {
        double value = o.has(key) ? o.get(key).getAsDouble() : fallback;
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid " + key);
        return value;
    }
    private static void keys(JsonObject o, Set<String> allowed) {
        for (String key : o.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown combat option: " + key);
    }
}
