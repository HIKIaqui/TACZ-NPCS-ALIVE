package mod.cdv.tnpc.dialogue;

import com.google.gson.*;
import java.util.*;
import net.aviel.dialogue.npc.dialogue.DialogueCondition;

/** One effective score for every dialogue reputation check; ADM owns expected/all/any/not. */
public final class ReputationDialogue {
    private ReputationDialogue() {}
    public static boolean matches(long effective,DialogueCondition.Predicate condition) {
        Double min=condition.min(),max=condition.max();
        return (min==null || Double.isFinite(min) && effective>=min)
            && (max==null || Double.isFinite(max) && effective<=max)
            && (min==null || max==null || min<=max);
    }
    public static void validate(JsonElement value) {
        if (value.isJsonArray()) { for (var e:value.getAsJsonArray()) validate(e); return; }
        if (!value.isJsonObject()) return;
        var o=value.getAsJsonObject();
        if (o.has("type") && o.get("type").isJsonPrimitive() && o.get("type").getAsString().equalsIgnoreCase("tnpc.reputation")) {
            for (String key:o.keySet()) if (!Set.of("type","min","max","amount","expected").contains(key))
                throw new IllegalArgumentException("tnpc.reputation does not accept "+key+"; use min/max or amount");
            Double min=bound(o,"min"),max=bound(o,"max"),amount=bound(o,"amount");
            if (amount!=null && (min!=null || max!=null)) throw new IllegalArgumentException("Use amount OR min/max for reputation");
            if (min==null && max==null && amount==null) throw new IllegalArgumentException("Reputation condition requires min, max or amount");
            if (min!=null && max!=null && min>max) throw new IllegalArgumentException("Reputation min exceeds max");
            if (o.has("expected") && (!o.get("expected").isJsonPrimitive() || !o.get("expected").getAsJsonPrimitive().isBoolean()))
                throw new IllegalArgumentException("Reputation expected must be boolean");
        }
        for (var e:o.entrySet()) validate(e.getValue());
    }
    private static Double bound(JsonObject o,String key) {
        if (!o.has(key)) return null;
        var e=o.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Reputation "+key+" must be numeric");
        long n=e.getAsBigDecimal().longValueExact();
        if (n < -2_000_000_000L || n>2_000_000_000L) throw new IllegalArgumentException("Reputation condition outside effective score range");
        return (double)n;
    }
}
