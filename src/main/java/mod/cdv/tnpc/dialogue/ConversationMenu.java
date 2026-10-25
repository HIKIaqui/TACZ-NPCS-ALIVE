package mod.cdv.tnpc.dialogue;

import com.google.gson.*;
import java.util.*;
import java.util.function.IntUnaryOperator;
import mod.cdv.tnpc.quest.QuestDefinition;
import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;

/** A private, immutable snapshot per conversation; never edits a shared NPC profile. */
public final class ConversationMenu {
    private static final List<String> NODES = List.of("questions", "activity", "news", "help", "follow", "other");
    private ConversationMenu() {}
    public static String compile(String json, QuestDefinition.Lines quest, DialogueReplies.Lines replies,
                                 IntUnaryOperator random) {
        return compile(json,quest,replies,random,null);
    }
    public static String compile(String json, QuestDefinition.Lines quest, DialogueReplies.Lines replies,
                                 IntUnaryOperator random, String reputationGreeting) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject nodes = root.getAsJsonObject("nodes");
        String start = root.get("start").getAsString();
        JsonObject greeting = nodes.getAsJsonObject(start);
        if (reputationGreeting != null) greeting.addProperty("text",reputationGreeting);
        JsonArray original = greeting.has("choices") ? greeting.getAsJsonArray("choices").deepCopy() : new JsonArray();
        String prefix = "tnpc_menu_";
        while (collides(nodes, prefix)) prefix = "_" + prefix;
        String questions = prefix + "questions";

        JsonElement activity = response(quest.activity(), replies.activity(),
            legacyText(nodes, original, "What are you doing here?", "Catching my breath. Then I'll see what the road brings."), random);
        JsonElement news = response(quest.news(), replies.news(),
            legacyText(nodes, original, "Any news around here?", "Nothing I can confirm. Keep your eyes open and watch your back."), random);
        JsonArray main = new JsonArray();
        JsonObject trade = choice("Let's Trade.", null); trade.addProperty("id", "tnpc_inventory_trade");
        trade.addProperty("trade", "tnpc_inventory.json");
        JsonObject condition = new JsonObject(); condition.addProperty("type", "tnpc.can_trade"); trade.add("condition", condition);
        main.add(trade); main.add(choice("I Want to Ask Something.", questions)); main.add(goodbye());
        greeting.add("choices", main);

        JsonArray menu = new JsonArray();
        menu.add(choice("Need help with something?", prefix + "help"));
        menu.add(choice("What are you doing here?", prefix + "activity"));
        menu.add(choice("Any news around here?", prefix + "news"));
        menu.add(choice("Want to travel with me?", prefix + "follow"));
        JsonArray extra = new JsonArray();
        for (var element : original) {
            JsonObject c = element.getAsJsonObject();
            if (!isStandard(c)) extra.add(c.deepCopy());
        }
        if (!extra.isEmpty()) {
            menu.add(choice("Other questions.", prefix + "other"));
            extra.add(choice("Back.", questions));
            nodes.add(prefix + "other", node(new JsonPrimitive("What else?"), extra));
        }
        menu.add(choice("Back.", start));
        nodes.add(questions, node(new JsonPrimitive("What do you want to know?"), menu));
        reply(nodes, prefix + "activity", activity, questions);
        reply(nodes, prefix + "news", news, questions);
        reply(nodes, prefix + "help", response(List.of(), replies.help(),
            new JsonPrimitive("I'm not looking for supplies right now."), random), questions);
        reply(nodes, prefix + "follow", response(List.of(), replies.follow(),
            new JsonPrimitive("I'm travelling on my own for now."), random), questions);
        String result = root.toString();
        if (result.length() > 30000) throw new IllegalArgumentException("Conversation exceeds ADM packet limit");
        NpcDialogueDefinition.fromJson(result);
        return result;
    }
    private static boolean collides(JsonObject nodes, String prefix) {
        return NODES.stream().anyMatch(name -> nodes.has(prefix + name));
    }
    private static boolean isStandard(JsonObject c) {
        String id = c.has("id") ? c.get("id").getAsString() : "";
        if (id.equals("tnpc_inventory_trade") || id.equals("tnpc_inventory_gift")) return true;
        if (c.has("close") && c.get("close").getAsBoolean()
            && Set.of("text", "close", "id").containsAll(c.keySet())) return true;
        String text = c.has("text") ? c.get("text").getAsString() : "";
        return Set.of("Any news around here?", "What are you doing here?").contains(text)
            && Set.of("text", "next", "id").containsAll(c.keySet());
    }
    private static JsonElement legacyText(JsonObject nodes, JsonArray choices, String label, String fallback) {
        for (var element : choices) {
            var c = element.getAsJsonObject();
            if (c.has("text") && c.get("text").getAsString().equals(label) && c.has("next")
                && Set.of("text", "next", "id").containsAll(c.keySet())) {
                var node = nodes.getAsJsonObject(c.get("next").getAsString());
                if (node != null && node.has("text")) return node.get("text").deepCopy();
            }
        }
        return new JsonPrimitive(fallback);
    }
    private static JsonElement response(List<String> quest, List<String> fallback, JsonElement builtin, IntUnaryOperator random) {
        List<String> lines = quest.isEmpty() ? fallback : quest;
        return lines.isEmpty() ? builtin : new JsonPrimitive(lines.get(random.applyAsInt(lines.size())));
    }
    private static JsonObject choice(String text, String next) {
        var c = new JsonObject(); c.addProperty("text", text); if (next != null) c.addProperty("next", next); return c;
    }
    private static JsonObject goodbye() {
        var c = choice("Goodbye.", null); c.addProperty("close", true); return c;
    }
    private static JsonObject node(JsonElement text, JsonArray choices) {
        var n = new JsonObject(); n.add("text", text); n.add("choices", choices); return n;
    }
    private static void reply(JsonObject nodes, String id, JsonElement text, String questions) {
        var choices = new JsonArray(); choices.add(choice("Back.", questions)); nodes.add(id, node(text, choices));
    }
}
