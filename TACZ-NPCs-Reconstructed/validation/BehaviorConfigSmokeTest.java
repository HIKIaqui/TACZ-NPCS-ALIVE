import java.nio.file.Files;
import java.nio.file.Path;
import mod.cdv.tnpc.data.NpcBehaviorConfig;

/** Exercises the real config loader without launching Minecraft or adding test libraries. */
public class BehaviorConfigSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("tnpc-behavior-validation-");
        Path config = directory.resolve("tacznpc_behavior.json");
        check(NpcBehaviorConfig.loadFile(config), "First load creates defaults");
        check(Files.isRegularFile(config), "Config file was created");
        check(NpcBehaviorConfig.restFor("default").enabled(), "Default rest enabled");
        Files.writeString(config, """
            {"rest":{"speed":1.25,"minDurationTicks":300,"maxDurationTicks":900},
             "loadouts":{"guard":{"rest":{"enabled":false}},
                         "scout":{"rest":{"searchRadius":40}}}}
            """);
        check(NpcBehaviorConfig.loadFile(config), "Overrides accepted");
        check(!NpcBehaviorConfig.restFor("guard").enabled(), "Guard override applied");
        check(NpcBehaviorConfig.restFor("guard").speed() == 1.25, "Guard inherits global speed");
        check(NpcBehaviorConfig.restFor("scout").searchRadius() == 40, "Scout radius override applied");
        check(NpcBehaviorConfig.restFor("scout").maxDurationTicks() == 900, "Scout inherits duration");
        check(NpcBehaviorConfig.restFor("unknown").speed() == 1.25, "Unknown loadout inherits defaults");
        var saved = NpcBehaviorConfig.restFor("guard");
        for (String invalid : new String[] {
            "{\"rest\":{\"minDurationTicks\":900,\"maxDurationTicks\":100}}",
            "{\"rest\":{\"searchIntervalTicks\":0}}",
            "{\"rest\":{\"searchRadius\":12.5}}",
            "{\"rest\":{\"chance\":2}}",
            "{broken json"
        }) {
            Files.writeString(config, invalid);
            check(!NpcBehaviorConfig.loadFile(config), "Invalid config rejected");
            check(NpcBehaviorConfig.restFor("guard") == saved, "Failed reload retains snapshot");
        }
        Files.writeString(config, "{}");
        check(NpcBehaviorConfig.loadFile(config), "Empty object uses builtin defaults");
        check(NpcBehaviorConfig.restFor("guard").enabled(), "Removed overrides no longer apply");
        Files.delete(config);
        Files.delete(directory);
        System.out.println("PASS: creation, inheritance, per-loadout overrides, invalid ranges, fractional ticks, malformed JSON and atomic reload retention.");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
