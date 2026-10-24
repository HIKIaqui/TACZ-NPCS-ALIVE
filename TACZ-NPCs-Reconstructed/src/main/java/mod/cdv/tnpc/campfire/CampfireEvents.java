package mod.cdv.tnpc.campfire;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CampfireEvents {
    public static final RestReservations RESERVATIONS = new RestReservations();
    @SubscribeEvent public void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            long position = chunk.getPos().toLong();
            level.getServer().execute(() -> CampfireTimers.get(level).chunkLoaded(position));
        }
    }
    @SubscribeEvent public void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            long position = event.getChunk().getPos().toLong();
            level.getServer().execute(() -> CampfireTimers.get(level).chunkUnloaded(position));
        }
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) CampfireTimers.get(level).tick(level);
    }
    @SubscribeEvent public void left(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide) RESERVATIONS.release(event.getEntity().getUUID());
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { RESERVATIONS.clear(); }
}
