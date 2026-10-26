package mod.cdv.tnpc.requests;

import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public final class RequestConfig {
    public record Profile(boolean enabled,int personalReward,int factionReward,int cooldownTicks,int minimumReputation) {}
    public record Settings(Profile defaults,Map<String,Profile> loadouts) {
        public Profile forLoadout(String id) { return loadouts.getOrDefault(id,defaults); }
    }
    public static final String DEFAULT_JSON="""
        {"defaults":{"enabled":true,"personalReward":5,"factionReward":0,"cooldownTicks":24000,"minimumReputation":-49},"loadouts":{}}
        """;
    public static final Settings DEFAULT=parse(DEFAULT_JSON);
    public static Settings read(Path file) throws IOException {
        if(Files.notExists(file)) Files.writeString(file,DEFAULT_JSON);
        return parse(Files.readString(file));
    }
    public static Settings parse(String json) {
        var o=JsonParser.parseString(json).getAsJsonObject(); keys(o,Set.of("defaults","loadouts"));
        var builtin=new Profile(true,5,0,24000,-49);
        var defaults=read(o.has("defaults") ? o.getAsJsonObject("defaults") : new JsonObject(),builtin);
        Map<String,Profile> profiles=new HashMap<>();
        if(o.has("loadouts")) for(var e:o.getAsJsonObject("loadouts").entrySet()) {
            if(e.getKey().isBlank()) throw new IllegalArgumentException("Blank request loadout");
            profiles.put(e.getKey(),read(e.getValue().getAsJsonObject(),defaults));
        }
        return new Settings(defaults,Map.copyOf(profiles));
    }
    private static Profile read(JsonObject o,Profile f) {
        keys(o,Set.of("enabled","personalReward","factionReward","cooldownTicks","minimumReputation"));
        boolean enabled=f.enabled();
        if(o.has("enabled")) { if(!o.get("enabled").isJsonPrimitive() || !o.getAsJsonPrimitive("enabled").isBoolean()) throw new IllegalArgumentException("enabled must be boolean"); enabled=o.get("enabled").getAsBoolean(); }
        var p=new Profile(enabled,integer(o,"personalReward",f.personalReward()),integer(o,"factionReward",f.factionReward()),
            integer(o,"cooldownTicks",f.cooldownTicks()),integer(o,"minimumReputation",f.minimumReputation()));
        if(p.personalReward()<0 || p.personalReward()>1000000 || p.factionReward()<0 || p.factionReward()>1000000 || p.cooldownTicks()<0 || p.cooldownTicks()>1728000
            || p.minimumReputation() < -1000000000 || p.minimumReputation()>1000000000) throw new IllegalArgumentException("Invalid request settings");
        return p;
    }
    private static int integer(JsonObject o,String key,int fallback) {
        if(!o.has(key)) return fallback;
        var v=o.get(key); if(!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer: "+key);
        return v.getAsBigDecimal().intValueExact();
    }
    private static void keys(JsonObject o,Set<String> allowed) { for(String k:o.keySet()) if(!allowed.contains(k)) throw new IllegalArgumentException("Unknown request setting: "+k); }
}
