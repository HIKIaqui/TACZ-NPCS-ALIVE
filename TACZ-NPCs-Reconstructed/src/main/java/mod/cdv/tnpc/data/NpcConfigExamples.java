package mod.cdv.tnpc.data;

import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.server.MinecraftServer;

/** Separate reference files never replace an existing server's configuration. */
public final class NpcConfigExamples {
    public static void install(MinecraftServer server) {
        Path root = server.getServerDirectory().resolve("config/tacznpc_examples/biome-loot-v0.2");
        try (var manifest = NpcConfigExamples.class.getResourceAsStream("/npc_examples/manifest.txt")) {
            if (manifest == null) throw new java.io.IOException("Missing example manifest");
            for (String name : new String(manifest.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (name.isBlank()) continue;
                Path path = root.resolve(name).normalize();
                if (!path.startsWith(root)) throw new java.io.IOException("Invalid example path");
                if (Files.exists(path)) continue;
                Files.createDirectories(path.getParent());
                try (var input = NpcConfigExamples.class.getResourceAsStream("/npc_examples/" + name)) {
                    if (input == null) throw new java.io.IOException("Missing example " + name);
                    Files.copy(input, path);
                }
            }
        } catch (Exception e) { LogUtils.getLogger().error("Could not install NPC configuration examples", e); }
    }
}
