package mod.cdv.tnpc.quest;

import java.util.ArrayList;
import java.util.List;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class NpcQuests {
    public static final ResourceLocation HEALTH_MODIFIER = ResourceLocation.fromNamespaceAndPath("tacznpcs", "quest_level_health");
    private NpcQuests() {}
    public static void tick(AutoGunOperator npc, ServerLevel level) {
        var settings = QuestConfig.settings();
        if (npc.getQuestState().appliedSettings() != settings) applyHealth(npc, true);
        if (!settings.enabled() || !npc.isAlive() || npc.isNoAi() || mod.cdv.tnpc.companion.Companions.active(npc)) return;
        // Spread checks across NPCs. No global entity scan and no per-tick world queries.
        int offset = Math.floorMod(npc.getUUID().hashCode(), settings.checkIntervalTicks());
        if (Math.floorMod((long)npc.tickCount + offset, settings.checkIntervalTicks()) == 0) check(npc, level);
    }
    public static boolean check(AutoGunOperator npc, ServerLevel level) {
        var settings = QuestConfig.settings(); var state = npc.getQuestState();
        if (!settings.enabled() || !npc.isAlive() || mod.cdv.tnpc.companion.Companions.active(npc)) return false;
        long now = level.getServer().overworld().getGameTime();
        if (state.active() == null && now < state.nextAssignmentAt()) return false;
        var definitions = settings.quests().values().stream().filter(q -> q.enabled()
            && (q.allowsLoadout(npc.getTemplateId()) || (state.active() != null && q.id().equals(state.active().id())))).toList();
        var place = QuestLocation.inspect(level, npc.blockPosition(), definitions);
        if (!place.complete()) return false;
        boolean completed = false;
        var active = state.active();
        if (active != null) {
            var definition = settings.quests().get(active.id());
            if (definition == null || !definition.enabled()) state.clear(0);
            else if (definition.completedAt(active, place)) {
                completed = state.complete(active.serial(), definition.xp());
                if (completed) applyHealth(npc, true);
            }
        }
        if (state.active() == null) {
            List<QuestDefinition> candidates = new ArrayList<>(); int total = 0;
            for (var quest : settings.quests().values()) if (quest.eligible(npc.getTemplateId(), place)) {
                candidates.add(quest); total += quest.weight();
            }
            if (total > 0) {
                int roll = npc.getRandom().nextInt(total);
                for (var quest : candidates) {
                    roll -= quest.weight();
                    if (roll < 0) { state.assign(quest.id(), place, now); break; }
                }
            } else state.clear(now + settings.assignmentRetryTicks());
        }
        return completed;
    }
    public static boolean assign(AutoGunOperator npc, String id, ServerLevel level) {
        var settings = QuestConfig.settings(); var definition = settings.quests().get(id);
        if (!settings.enabled() || definition == null || !npc.isAlive()) return false;
        var place = QuestLocation.inspect(level, npc.blockPosition(), List.of(definition));
        return place.complete() && definition.eligible(npc.getTemplateId(), place)
            && npc.getQuestState().assign(id, place, level.getServer().overworld().getGameTime());
    }
    public static long level(AutoGunOperator npc) {
        return QuestConfig.settings().progression().level(npc.getQuestState().xp());
    }
    public static void initializeLevel(AutoGunOperator npc) {
        var settings = QuestConfig.settings();
        long required = settings.progression().xpForLevel(settings.minSpawnLevel(npc.getTemplateId()));
        if (npc.getQuestState().xp() < required) addXp(npc, required - npc.getQuestState().xp());
    }
    public static void addXp(AutoGunOperator npc, long amount) {
        npc.getQuestState().addXp(amount); applyHealth(npc, true);
    }
    public static void applyHealth(AutoGunOperator npc, boolean grantNewCapacity) {
        var attribute = npc.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return;
        float oldMax = npc.getMaxHealth(), oldHealth = npc.getHealth();
        attribute.removeModifier(HEALTH_MODIFIER);
        var settings = QuestConfig.settings();
        double bonus = settings.progression().bonus(npc.getQuestState().xp(), attribute.getBaseValue());
        if (bonus > 0) attribute.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER, bonus, AttributeModifier.Operation.ADD_VALUE));
        float capacity = grantNewCapacity && npc.isAlive() ? Math.max(0, npc.getMaxHealth() - oldMax) : 0;
        npc.setHealth(Math.min(npc.getMaxHealth(), oldHealth + capacity));
        npc.getQuestState().appliedSettings(settings);
    }
    public static void load(AutoGunOperator npc, CompoundTag tag) {
        npc.getQuestState().load(tag);
        applyHealth(npc, false);
        // Health was read by LivingEntity before our modifier: restore its original saved value.
        if (tag.contains("Health", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC))
            npc.setHealth(Math.min(npc.getMaxHealth(), Math.max(0, tag.getFloat("Health"))));
    }
    /** Future dialogue integration; missing topics return empty so the menu can use its own fallback. */
    public static QuestDefinition.Lines dialogueLines(AutoGunOperator npc) {
        var active = npc.getQuestState().active();
        var quest = active == null ? null : QuestConfig.settings().quests().get(active.id());
        return quest == null || !quest.enabled() ? QuestDefinition.Lines.EMPTY : quest.dialogues().forLoadout(npc.getTemplateId());
    }
}
