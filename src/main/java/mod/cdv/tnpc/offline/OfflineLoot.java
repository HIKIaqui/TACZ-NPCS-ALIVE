package mod.cdv.tnpc.offline;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import mod.cdv.tnpc.inventory.NpcInventoryConfig;

/** Files are decoded on reload. Entity ticks only sample validated in-memory pools. */
public final class OfflineLoot {
    private record Range(int min, int max) {
        int roll(RandomSource random) { return min == max ? min : min + random.nextInt(max - min + 1); }
    }
    private record Entry(ItemStack prototype, int weight, double chance, Range count) {}
    private record Pool(String name, Range rolls, double chance, List<Entry> entries, int totalWeight) {}
    public static final class Table {
        private final String file;
        private final List<Pool> pools;
        private Table(String file, List<Pool> pools) { this.file = file; this.pools = List.copyOf(pools); }
        public String file() { return file; }
    }
    public record Snapshot(Table fallback, Map<ResourceLocation, Table> biomes) {
        public Snapshot { biomes = Map.copyOf(biomes); }
        public Table select(ResourceLocation biome) { return biome == null ? fallback : biomes.getOrDefault(biome, fallback); }
    }
    private static volatile Snapshot current = new Snapshot(null, Map.of());
    private OfflineLoot() {}
    static void install(Snapshot snapshot) { current = snapshot; }
    public static Snapshot readDirectory(Path directory, HolderLookup.Provider registries) throws IOException {
        boolean fresh = Files.notExists(directory);
        Files.createDirectories(directory);
        for (String name : fresh ? List.of("default.json", "desert.json", "snowy.json") : List.of("default.json")) {
            Path file = directory.resolve(name);
            if (Files.notExists(file)) {
                try (var stream = OfflineLoot.class.getResourceAsStream("/npc_examples/config/tacznpc_loot/" + name)) {
                    if (stream == null) throw new IOException("Missing packaged biome loot list: " + name);
                    Files.copy(stream, file);
                }
            }
        }
        Map<ResourceLocation, Table> biomes = new LinkedHashMap<>();
        Table fallback = null;
        try (var files = Files.list(directory)) {
            var paths = files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
            if (paths.size() > 128) throw new IllegalArgumentException("Maximum 128 loot lists");
            for (var path : paths) {
                String name = path.getFileName().toString();
                try {
                    if (!name.matches("[a-z0-9_-]+\\.json")) throw new IllegalArgumentException("Use lowercase letters, digits, _ or - in filenames");
                    if (Files.size(path) > 2 * 1024 * 1024) throw new IllegalArgumentException("Loot file exceeds 2 MiB");
                    JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                    Table table = parse(name, root, registries);
                    if (name.equals("default.json")) {
                        if (root.has("biomes")) throw new IllegalArgumentException("default.json is the fallback; put biome selectors in other files");
                        fallback = table;
                    } else {
                        JsonArray selectors = root.getAsJsonArray("biomes");
                        if (selectors == null || selectors.isEmpty() || selectors.size() > 256)
                            throw new IllegalArgumentException("Expected 1..256 biome IDs in biomes");
                        for (var value : selectors) {
                            ResourceLocation biome = ResourceLocation.parse(value.getAsString());
                            var registry = registries.lookup(Registries.BIOME);
                            if (registry.isPresent() && registry.get().get(ResourceKey.create(Registries.BIOME, biome)).isEmpty())
                                throw new IllegalArgumentException("Unknown biome: " + biome);
                            Table duplicate = biomes.putIfAbsent(biome, table);
                            if (duplicate != null) throw new IllegalArgumentException("Duplicate biome " + biome + " in " + duplicate.file() + " and " + name);
                        }
                    }
                } catch (Exception e) { throw new IOException("Invalid loot list " + path + ": " + e.getMessage(), e); }
            }
        }
        if (fallback == null) throw new IOException("Missing default.json biome loot fallback");
        return new Snapshot(fallback, biomes);
    }
    private static Table parse(String file, JsonObject root, HolderLookup.Provider registries) throws Exception {
        if (root.has("loadouts")) throw new IllegalArgumentException("Legacy loadout loot format. Move old files outside tacznpc_loot and copy the biome examples from config/tacznpc_examples/biome-loot-v0.2/config/tacznpc_loot. Existing files are not overwritten.");
        JsonArray poolsJson = root.getAsJsonArray("pools");
        if (poolsJson == null || poolsJson.size() > 16) throw new IllegalArgumentException("Expected pools array, maximum 16 pools");
        List<Pool> pools = new ArrayList<>(); Set<String> names = new HashSet<>(); int maxRolls = 0;
        for (var poolValue : poolsJson) {
            JsonObject pool = poolValue.getAsJsonObject();
            String name = pool.has("name") ? pool.get("name").getAsString() : "pool_" + pools.size();
            if (!names.add(name)) throw new IllegalArgumentException("Duplicate pool: " + name);
            try {
                Range rolls = range(pool, "rolls", 1, 0, 16); maxRolls += rolls.max();
                if (maxRolls > 64) throw new IllegalArgumentException("Maximum 64 total rolls per action");
                double chance = chance(pool);
                JsonArray entriesJson = pool.getAsJsonArray("entries");
                if (entriesJson == null || entriesJson.isEmpty() || entriesJson.size() > 256)
                    throw new IllegalArgumentException("Expected 1..256 entries");
                List<Entry> entries = new ArrayList<>(); int totalWeight = 0;
                for (var entryValue : entriesJson) {
                    JsonObject entry = entryValue.getAsJsonObject();
                    try {
                        int weight = integer(entry, "weight", 1, 1, 1000000); totalWeight = Math.addExact(totalWeight, weight);
                        JsonObject serialized = new JsonObject(); serialized.addProperty("id", entry.get("item").getAsString()); serialized.addProperty("count", 1);
                        if (entry.has("components")) serialized.add("components", entry.getAsJsonObject("components").deepCopy());
                        ItemStack prototype = ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), serialized).getOrThrow();
                        if (prototype.isEmpty()) throw new IllegalArgumentException("Loot cannot be air");
                        if (entry.has("nbt")) {
                            if (entry.has("components") && entry.getAsJsonObject("components").has("minecraft:custom_data"))
                                throw new IllegalArgumentException("Use either nbt or components.minecraft:custom_data, not both");
                            var nbt = entry.get("nbt");
                            String snbt = nbt.isJsonObject() ? nbt.toString() : nbt.getAsString();
                            prototype.set(DataComponents.CUSTOM_DATA, CustomData.of(TagParser.parseTag(snbt)));
                        }
                        Range count = range(entry, "count", 1, 1, Math.min(64, prototype.getMaxStackSize()));
                        entries.add(new Entry(prototype, weight, chance(entry), count));
                    } catch (Exception e) { throw new IllegalArgumentException("entry " + entries.size() + ": " + e.getMessage(), e); }
                }
                pools.add(new Pool(name, rolls, chance, List.copyOf(entries), totalWeight));
            } catch (Exception e) { throw new IllegalArgumentException("pool " + name + ": " + e.getMessage(), e); }
        }
        return new Table(file, pools);
    }
    private static Table tableFor(AutoGunOperator npc) {
        var biome = npc.level().getBiome(npc.blockPosition()).unwrapKey().map(ResourceKey::location).orElse(null);
        return current.select(biome);
    }
    public static boolean hasPools(AutoGunOperator npc) {
        Table table = tableFor(npc);
        return table != null && !table.pools.isEmpty();
    }
    public static List<ItemStack> roll(Table table, RandomSource random) {
        List<ItemStack> result = new ArrayList<>();
        for (Pool pool : table.pools) {
            if (random.nextDouble() >= pool.chance()) continue;
            int rolls = pool.rolls().roll(random);
            for (int i = 0; i < rolls; i++) {
                int selection = random.nextInt(pool.totalWeight());
                for (Entry entry : pool.entries()) {
                    selection -= entry.weight();
                    if (selection >= 0) continue;
                    if (random.nextDouble() < entry.chance()) result.add(entry.prototype().copyWithCount(entry.count().roll(random)));
                    break; // Failed chance means an empty roll, never a free reroll.
                }
            }
        }
        return result;
    }
    public static List<ItemStack> choose(List<ItemStack> found, NpcInventoryConfig.Profile preferences) {
        return found.stream().filter(preferences::interested).map(ItemStack::copy).toList();
    }
    public static boolean loot(AutoGunOperator npc) {
        Table table = tableFor(npc);
        if (table == null || table.pools.isEmpty()) return false;
        // Generate every candidate before consulting interests: no weight adjustment or free reroll.
        List<ItemStack> found = roll(table, npc.getRandom());
        for (ItemStack stack : choose(found, NpcInventoryConfig.forLoadout(npc.getTemplateId()))) npc.getNpcInventory().store(stack);
        return true; // Searching consumed time even when nothing was found or accepted.
    }
    private static Range range(JsonObject o, String key, int fallback, int min, int max) {
        if (!o.has(key)) return new Range(fallback, fallback);
        var value = o.get(key);
        if (value.isJsonPrimitive()) { int n = exact(value,min,max); return new Range(n,n); }
        var object = value.getAsJsonObject();
        int low = exact(object.get("min"),min,max), high = exact(object.get("max"),min,max);
        if (low > high) throw new IllegalArgumentException(key + ": min exceeds max");
        return new Range(low, high);
    }
    private static int integer(JsonObject o, String key, int fallback, int min, int max) {
        return o.has(key) ? exact(o.get(key),min,max) : fallback;
    }
    private static int exact(JsonElement value, int min, int max) {
        int n = value.getAsBigDecimal().intValueExact();
        if (n < min || n > max) throw new IllegalArgumentException("Expected integer " + min + ".." + max);
        return n;
    }
    private static double chance(JsonObject o) {
        double chance = o.has("chance") ? o.get("chance").getAsDouble() : 1;
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("chance must be 0..1");
        return chance;
    }
}
