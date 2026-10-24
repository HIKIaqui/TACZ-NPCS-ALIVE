package mod.cdv.tnpc.inventory;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;

/** Reloadable interests and finite-stock shop policy. A failed reload keeps the previous snapshot. */
public final class NpcInventoryConfig {
    public record Profile(boolean canTrade, String tradeFile, boolean shareItems, double shareChance,
                          int shareCooldownTicks, List<String> interests) {
        public boolean interested(ItemStack stack) {
            if (stack.isEmpty()) return false;
            for (String selector : interests) {
                boolean tag = selector.startsWith("#");
                var id = ResourceLocation.parse(tag ? selector.substring(1) : selector);
                if (tag ? stack.is(TagKey.create(Registries.ITEM, id)) : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id)) return true;
            }
            return false;
        }
    }
    public record Generic(String currency, int quantity, int buyPrice, int sellPrice) {}
    private record Snapshot(Profile defaults, Map<String, Profile> loadouts,
                            Map<String,String> overrides, Map<String,String> factions, Generic generic) {}
    private static final Profile BUILTIN = new Profile(true, "", true, 0.1, 1200, List.of());
    private static volatile Snapshot current = parse("{}");
    public static Profile forLoadout(String id) { return current.loadouts().getOrDefault(id, current.defaults()); }
    public static Generic generic() { return current.generic(); }
    /** Multiple matching factions are resolved by lexicographic faction ID, independent of set iteration order. */
    public static String tradeFile(String loadout, Collection<String> factions) {
        var snapshot = current;
        if (snapshot.overrides().containsKey(loadout)) return snapshot.overrides().get(loadout);
        for (String faction : new TreeSet<>(factions)) if (snapshot.factions().containsKey(faction)) return snapshot.factions().get(faction);
        return snapshot.defaults().tradeFile();
    }
    public static boolean load(MinecraftServer server) {
        Path file = server.getServerDirectory().resolve("config/tacznpc_inventory.json");
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) Files.writeString(file, """
                {"defaults":{"canTrade":true,"tradeFile":"","interests":[],"shareItems":true,
                  "shareChance":0.1,"shareCooldownTicks":1200},"loadouts":{
                   "villagers":{"interests":["minecraft:bread","minecraft:carrot","minecraft:apple","minecraft:emerald","minecraft:iron_ingot","minecraft:leather"]},
                   "illagers":{"interests":["minecraft:bread","minecraft:iron_ingot","minecraft:emerald","tacz:modern_kinetic_gun"]}
                 },"factionTrades":{},
                 "genericTrades":{"currency":"minecraft:emerald","quantity":1,"buyPrice":1,"sellPrice":1}}
                """);
            current = parse(Files.readString(file)); return true;
        } catch (Exception e) {
            LogUtils.getLogger().error("Could not load NPC inventory configuration; retaining previous settings", e); return false;
        }
    }
    private static Snapshot parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Profile defaults = read(root.has("defaults") ? root.getAsJsonObject("defaults") : new JsonObject(), BUILTIN);
        Map<String,Profile> loadouts = new HashMap<>(); Map<String,String> overrides = new HashMap<>(), factions = new HashMap<>();
        if (root.has("loadouts")) for (var entry : root.getAsJsonObject("loadouts").entrySet()) {
            JsonObject object = entry.getValue().getAsJsonObject();
            Profile profile = read(object, defaults); loadouts.put(entry.getKey(), profile);
            if (object.has("tradeFile") && !profile.tradeFile().isEmpty()) overrides.put(entry.getKey(), profile.tradeFile());
        }
        if (root.has("factionTrades")) for (var entry : root.getAsJsonObject("factionTrades").entrySet()) {
            String file = normalizeFile(entry.getValue().getAsString());
            if (!file.isEmpty()) factions.put(entry.getKey(), file);
        }
        var g = root.has("genericTrades") ? root.getAsJsonObject("genericTrades") : new JsonObject();
        String currency = g.has("currency") ? g.get("currency").getAsString() : "minecraft:emerald";
        if (ResourceLocation.tryParse(currency) == null) throw new IllegalArgumentException("Invalid currency ID");
        Generic generic = new Generic(currency, positive(g,"quantity"), positive(g,"buyPrice"), positive(g,"sellPrice"));
        if (generic.buyPrice() > generic.sellPrice()) throw new IllegalArgumentException("buyPrice must not exceed sellPrice");
        return new Snapshot(defaults, Map.copyOf(loadouts), Map.copyOf(overrides), Map.copyOf(factions), generic);
    }
    private static int positive(JsonObject o, String name) {
        int value = o.has(name) ? o.get(name).getAsBigDecimal().intValueExact() : 1;
        if (value < 1 || value > 64) throw new IllegalArgumentException(name + " must be 1..64"); return value;
    }
    private static String normalizeFile(String file) {
        // This was the automatically installed v0.1 bread shop. It now means automatic offers.
        if (file.equals("tnpc_inventory.json")) return "";
        if (file.length() > 240) throw new IllegalArgumentException("Trade file reference too long");
        return file.trim();
    }
    private static Profile read(JsonObject o, Profile f) {
        List<String> interests = f.interests();
        if (o.has("interests")) {
            var selectors = new LinkedHashSet<String>();
            for (var entry : o.getAsJsonArray("interests")) {
                String value = entry.getAsString();
                if (ResourceLocation.tryParse(value.startsWith("#") ? value.substring(1) : value) == null)
                    throw new IllegalArgumentException("Invalid interest: " + value);
                selectors.add(value);
            }
            interests = List.copyOf(selectors);
        }
        Profile p = new Profile(o.has("canTrade") ? o.get("canTrade").getAsBoolean() : f.canTrade(),
            o.has("tradeFile") ? normalizeFile(o.get("tradeFile").getAsString()) : f.tradeFile(),
            o.has("shareItems") ? o.get("shareItems").getAsBoolean() : f.shareItems(),
            o.has("shareChance") ? o.get("shareChance").getAsDouble() : f.shareChance(),
            o.has("shareCooldownTicks") ? o.get("shareCooldownTicks").getAsBigDecimal().intValueExact() : f.shareCooldownTicks(), interests);
        if (!Double.isFinite(p.shareChance()) || p.shareChance() < 0 || p.shareChance() > 1
            || p.shareCooldownTicks() < 20 || p.shareCooldownTicks() > 1728000) throw new IllegalArgumentException("Invalid inventory profile");
        return p;
    }
}
