import java.nio.file.*;
import mod.cdv.tnpc.data.NpcBehaviorConfig;
import mod.cdv.tnpc.data.NpcBehaviorConfig.PerceptionSettings;
import mod.cdv.tnpc.entity.ai.NpcAwareness;
import mod.cdv.tnpc.entity.ai.NpcAwareness.State;

/** Exercises actual awareness transitions and the existing atomic, per-loadout configuration loader. */
public class AwarenessSmokeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        PerceptionSettings settings = new PerceptionSettings(true, 16, 6, 48, 600, 12);
        NpcAwareness state = new NpcAwareness();
        check(state.state(100, false) == State.CALM, "New NPC starts calm");
        check(state.detectionRange(100, false, 48, settings) == 16, "Calm perception reduced");
        state.beginRest();
        check(state.state(100, false) == State.RESTING, "Arrival starts rest");
        check(state.detectionRange(100, false, 48, settings) == 6, "Rest perception drastically reduced");
        check(state.detectionRange(100, false, 4, settings) == 4, "FOLLOW_RANGE is never exceeded");
        state.alert(100, 600);
        check(state.state(100, false) == State.ALERT, "Damage wakes a resting NPC without requiring a target");
        check(state.detectionRange(100, false, 48, settings) == 48, "Threat restores alert perception immediately");
        check(!state.canRest(699, false, settings), "No return to POI during alert memory");
        state.endRest();
        state.alert(200, 600);
        state.alert(150, 600);
        check(state.alertUntil() == 800, "Repeated/older signals cannot shorten alert memory");
        check(state.state(799, false) == State.ALERT, "Memory stays active until deadline");
        check(state.state(800, false) == State.CALM, "No-target NPC calms at deadline");
        check(state.state(1000, true) == State.ALERT, "Active target prevents calming even beyond old deadline");
        check(!state.canRest(1000, true, settings), "Combat never allows rest");
        check(state.canRest(1000, false, settings), "Rest allowed again after alert memory");
        state.beginRest(); state.endRest();
        check(state.state(1000, false) == State.CALM, "Natural end/conversation interruption does not invent danger");
        state.alert(1000, 600);
        NpcAwareness reloaded = new NpcAwareness(); reloaded.restore(state.alertUntil());
        check(reloaded.state(1200, false) == State.ALERT, "Saved alert deadline survives reloading");
        check(reloaded.state(1600, false) == State.CALM, "Saved alert can expire while unloaded");
        PerceptionSettings disabled = new PerceptionSettings(false, 16, 6, 48, 600, 12);
        check(state.detectionRange(1000, false, 32, disabled) == 32, "Disabled feature restores attribute range");
        check(state.canRest(1000, false, disabled), "Disabled alert memory does not block rest");
        Path dir = Files.createTempDirectory("tnpc-awareness-"); Path config = dir.resolve("behavior.json");
        Files.writeString(config, "{\"rest\":{\"speed\":1.25},\"loadouts\":{\"guard\":{\"rest\":{\"enabled\":false}}}}");
        check(NpcBehaviorConfig.loadFile(config), "Old rest-only config remains compatible");
        check(NpcBehaviorConfig.perceptionFor("guard").restingRange() == 6, "Missing perception gets safe defaults");
        check(!NpcBehaviorConfig.restFor("guard").enabled(), "Existing rest override preserved");
        Files.writeString(config, "{\"perception\":{\"calmRange\":12,\"alertDurationTicks\":1000},\"loadouts\":{\"scout\":{\"perception\":{\"restingRange\":4}}}}");
        check(NpcBehaviorConfig.loadFile(config), "Perception overrides load");
        check(NpcBehaviorConfig.perceptionFor("scout").restingRange() == 4, "Per-loadout rest perception applies");
        check(NpcBehaviorConfig.perceptionFor("scout").calmRange() == 12, "Per-loadout inherits global perception");
        check(NpcBehaviorConfig.perceptionFor("unknown").alertDurationTicks() == 1000, "Unknown loadout inherits global deadline");
        var previous = NpcBehaviorConfig.perceptionFor("scout");
        for (String invalid : new String[] {
            "{\"perception\":{\"restingRange\":20}}",
            "{\"perception\":{\"calmRange\":50}}",
            "{\"perception\":{\"alertRange\":129}}",
            "{\"perception\":{\"allyAlertRadius\":-1}}",
            "{\"perception\":{\"alertDurationTicks\":0}}",
            "{\"perception\":{\"alertDurationTicks\":20.5}}",
            "{\"loadouts\":{\"scout\":{\"perception\":{\"restingRange\":100}}}}"
        }) {
            Files.writeString(config, invalid);
            check(!NpcBehaviorConfig.loadFile(config), "Invalid perception rejected");
            check(NpcBehaviorConfig.perceptionFor("scout") == previous, "Failed reload preserves previous settings atomically");
        }
        Files.delete(config); Files.delete(dir);
        System.out.println("PASS: " + checks + " awareness/state/configuration checks");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
}
