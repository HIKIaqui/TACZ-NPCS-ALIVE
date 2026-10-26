package mod.cdv.tnpc.inventory;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;

/** Reloadable interests and finite-stock shop policy. A failed reload keeps the previous snapshot. */
public final class NpcInventoryConfig {
    public static final int MAX_CARGO_SLOTS = net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE;
    public record Profile(boolean canTrade, boolean shareItems, double shareChance,
                          int shareCooldownTicks, List<InterestRule> interests, int maxCargoSlots, int pickupRetryTicks, Map<String,java.math.BigDecimal> reputationPricePercent) {
        public InterestRule rule(ItemStack stack) {
            InterestRule tag=null;
            for(var rule:interests) if(rule.matches(stack)) {
                if(!rule.tag()) return rule;
                if(tag==null) tag=rule;
            }
            return tag;
        }
        public boolean interested(ItemStack stack) { var r=rule(stack); return r!=null && r.weight()>0; }
        public double interest(ItemStack stack) { var r=rule(stack); return r==null ? 0 : r.weight(); }
        public java.util.OptionalInt basePrice(ItemStack stack) { var r=rule(stack); return r==null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(r.basePrice()); }
        public boolean protectedItem(ItemStack stack) { var r=rule(stack); return r!=null && r.protectedItem(); }
    }
    private record Snapshot(Profile defaults, Map<String,Profile> loadouts,List<CommerceSettings.Currency> currencies) {}
    private static final Profile BUILTIN = new Profile(true, true, 0.1, 1200, List.of(), 15, 100, CommerceSettings.DEFAULT_PERCENT);
    private static volatile Snapshot current = parse("{}");
    public static Profile forLoadout(String id) { return current.loadouts().getOrDefault(id, current.defaults()); }
    public static List<CommerceSettings.Currency> currencies() { return current.currencies(); }
    public static Object policyToken() { return current; }
    public static boolean load(MinecraftServer server) {
        return loadFile(server.getServerDirectory().resolve("config/tacznpc_inventory.json"));
    }
    public static boolean loadFile(Path file) {
        try {
            Files.createDirectories(file.getParent());
            if(Files.notExists(file)) try(var resource=NpcInventoryConfig.class.getResourceAsStream("/npc_examples/config/tacznpc_inventory.json")) {
                if(resource==null) throw new java.io.IOException("Missing bundled inventory defaults");
                Files.copy(resource,file);
            }
            var candidate = parse(Files.readString(file));
            // On a live server reject unknown currencies before replacing the previous policy.
            if (net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()!=null) CommerceSettings.solver(candidate.currencies());
            current = candidate; return true;
        } catch (Exception e) {
            LogUtils.getLogger().error("Could not load NPC inventory configuration; retaining previous settings", e); return false;
        }
    }
    private static Snapshot parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Profile defaults = read(root.has("defaults") ? root.getAsJsonObject("defaults") : new JsonObject(), BUILTIN);
        Map<String,Profile> loadouts=new HashMap<>();
        if(root.has("loadouts")) for(var e:root.getAsJsonObject("loadouts").entrySet())
            loadouts.put(e.getKey(),read(e.getValue().getAsJsonObject(),defaults));
        return new Snapshot(defaults,Map.copyOf(loadouts),CommerceSettings.currencies(root));
    }
    private static Profile read(JsonObject o, Profile f) {
        List<InterestRule> interests = f.interests();
        if (o.has("interests")) {
            List<InterestRule> rules = new ArrayList<>(); Set<String> selectors=new HashSet<>();
            for (var entry:o.getAsJsonArray("interests")) {
                var rule=InterestRule.parse(entry);
                if(!selectors.add(rule.selector())) throw new IllegalArgumentException("Duplicate interest: "+rule.selector());
                rules.add(rule);
            }
            interests=List.copyOf(rules);
        }
        Profile p = new Profile(o.has("canTrade") ? o.get("canTrade").getAsBoolean() : f.canTrade(),
            o.has("shareItems") ? o.get("shareItems").getAsBoolean() : f.shareItems(),
            o.has("shareChance") ? o.get("shareChance").getAsDouble() : f.shareChance(),
            o.has("shareCooldownTicks") ? o.get("shareCooldownTicks").getAsBigDecimal().intValueExact() : f.shareCooldownTicks(), interests,
            o.has("maxCargoSlots") ? o.get("maxCargoSlots").getAsBigDecimal().intValueExact() : f.maxCargoSlots(),
            o.has("pickupRetryTicks") ? o.get("pickupRetryTicks").getAsBigDecimal().intValueExact() : f.pickupRetryTicks(), CommerceSettings.percentages(o,f.reputationPricePercent()));
        if (p.maxCargoSlots()<1 || p.maxCargoSlots()>MAX_CARGO_SLOTS || p.pickupRetryTicks()<20 || p.pickupRetryTicks()>12000)
            throw new IllegalArgumentException("Invalid cargo capacity/retry interval");
        if (!Double.isFinite(p.shareChance()) || p.shareChance() < 0 || p.shareChance() > 1
            || p.shareCooldownTicks() < 20 || p.shareCooldownTicks() > 1728000) throw new IllegalArgumentException("Invalid inventory profile");
        return p;
    }
}
