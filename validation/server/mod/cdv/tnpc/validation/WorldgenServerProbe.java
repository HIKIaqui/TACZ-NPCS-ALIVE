package mod.cdv.tnpc.validation;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import mod.cdv.tnpc.data.*;
import mod.cdv.tnpc.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.*;

/** Real NeoForge server regression checks; never included in the distributable. */
@EventBusSubscriber(modid="tacznpcs")
public final class WorldgenServerProbe {
    private static final String PENDING="tacznpcs:pending_structure_replacement";
    private static final Path RESULT=Path.of("worldgen-probe-results.txt");
    private static int ticks, checks;
    private static boolean veto;
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (veto && event.getEntity() instanceof AutoGunOperator) event.setCanceled(true);
    }
    private static void check(boolean ok, String text) throws Exception {
        if (!ok) throw new AssertionError(text);
        checks++; Files.writeString(RESULT,"OK "+text+"\n",StandardOpenOption.APPEND);
    }
    private static Zombie mob(ServerLevel level) {
        Zombie z=new Zombie(EntityType.ZOMBIE,level); z.moveTo(.5,-60,.5,73,12); z.setNoAi(true); return z;
    }
    private static FinalizeSpawnEvent finalizeEvent(Mob mob, MobSpawnType reason) {
        return new FinalizeSpawnEvent(mob, (ServerLevel)mob.level(), mob.getX(),mob.getY(),mob.getZ(),null,reason,null,null);
    }
    private static long count(ServerLevel level) {
        long n=0; for (Entity e:level.getAllEntities()) if (e instanceof AutoGunOperator && !e.isRemoved()) n++; return n;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("tnpc.worldgenTest") || ++ticks!=10) return;
        Files.writeString(RESULT,"START\n");
        var server=event.getServer(); var level=server.overworld();
        try {
            level.getChunk(0,0);
            var pending=mob(level); var spawn=finalizeEvent(pending,MobSpawnType.STRUCTURE);
            ExecutorService worker=Executors.newSingleThreadExecutor();
            try { worker.submit(()->StructureNpcSpawns.onFinalizeSpawn(spawn)).get(10,TimeUnit.SECONDS); }
            finally { worker.shutdownNow(); }
            check(pending.getPersistentData().getBoolean(PENDING),"worker marks entity without accessing world RNG or structures");
            check(!spawn.isSpawnCancelled() && !pending.isRemoved(),"generation leaves original available for chunk serialization");
            var restored=mob(level); restored.load(pending.saveWithoutId(new CompoundTag()));
            check(restored.getPersistentData().getBoolean(PENDING),"pending marker survives entity NBT roundtrip");
            worker=Executors.newSingleThreadExecutor();
            try { worker.submit(()->StructureNpcSpawns.onEntityTick(new EntityTickEvent.Pre(restored))).get(10,TimeUnit.SECONDS); }
            finally { worker.shutdownNow(); }
            check(restored.getPersistentData().getBoolean(PENDING),"off-thread tick cannot consume marker or mutate world");
            for (MobSpawnType reason:List.of(MobSpawnType.SPAWNER,MobSpawnType.COMMAND,MobSpawnType.SPAWN_EGG)) {
                var excluded=mob(level); StructureNpcSpawns.onFinalizeSpawn(finalizeEvent(excluded,reason));
                check(!excluded.getPersistentData().contains(PENDING),"excluded spawn reason: "+reason);
            }
            var cancelled=mob(level); var cancelledEvent=finalizeEvent(cancelled,MobSpawnType.NATURAL); cancelledEvent.setSpawnCancelled(true);
            StructureNpcSpawns.onFinalizeSpawn(cancelledEvent);
            check(!cancelled.getPersistentData().contains(PENDING),"vetoed original spawn stays vetoed");
            var chunk=level.getChunk(0,0);
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("minecraft:buried_treasure"));
            chunk.setStartForStructure(structure,new StructureStart(structure,chunk.getPos(),0,new PiecesContainer(List.of(new BuriedTreasurePieces.BuriedTreasurePiece(new BlockPos(0,-60,0))))));
            chunk.addReferenceForStructure(structure,chunk.getPos().toLong());
            Path config=server.getServerDirectory().resolve("config/tacznpc_loadouts.json");
            Files.writeString(config,"{\"probe\":{\"faction\":\"survivors\",\"relations\":{\"monster\":-100}}}");
            check(TemplateDataManager.init(server),"current reputation loadout schema loads");
            var rules=new ArrayList<StructureTemplate>();
            rules.add(new StructureTemplate("minecraft:buried_treasure",Set.of("minecraft:zombie"),"probe",0));
            TemplateDataManager.STRUCTURES.put("minecraft:buried_treasure",rules);
            StructureNpcSpawns.onEntityTick(new EntityTickEvent.Pre(restored));
            check(!restored.isRemoved() && !restored.getPersistentData().contains(PENDING),"zero chance retains original and consumes attempt");
            rules.set(0,new StructureTemplate("minecraft:buried_treasure",Set.of("minecraft:zombie"),"probe",1));
            StructureNpcSpawns.onEntityTick(new EntityTickEvent.Pre(restored));
            check(!restored.isRemoved(),"subsequent ticks cannot retry failed chance");
            rules.add(rules.getFirst());
            var success=mob(level); level.addFreshEntity(success);
            StructureNpcSpawns.onFinalizeSpawn(finalizeEvent(success,MobSpawnType.NATURAL));
            long before=count(level); var tick=new EntityTickEvent.Pre(success); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(tick);
            check(success.isRemoved() && tick.isCanceled(),"successful replacement discards original and cancels its tick");
            check(count(level)==before+1,"overlapping matching rules create exactly one NPC");
            AutoGunOperator npc=null; for (Entity e:level.getAllEntities()) if (e instanceof AutoGunOperator op && !op.isRemoved()) npc=op;
            check(npc!=null && npc.position().equals(success.position()) && npc.getYRot()==73 && npc.getXRot()==12,"replacement preserves position and rotation");
            check(npc.getFactionId().equals("survivors"),"replacement keeps current reputation/faction configuration");
            StructureNpcSpawns.onFinalizeSpawn(finalizeEvent(npc,MobSpawnType.STRUCTURE));
            check(!npc.getPersistentData().contains(PENDING),"NPC cannot schedule recursive replacement");
            var rejected=mob(level); level.addFreshEntity(rejected);
            StructureNpcSpawns.onFinalizeSpawn(finalizeEvent(rejected,MobSpawnType.STRUCTURE));
            veto=true; var rejectedTick=new EntityTickEvent.Pre(rejected); StructureNpcSpawns.onEntityTick(rejectedTick); veto=false;
            check(!rejected.isRemoved() && !rejectedTick.isCanceled(),"replacement veto preserves original mob");
            check(!rejected.getPersistentData().contains(PENDING),"replacement veto does not cause infinite retries");
            Files.writeString(RESULT,"PASSED "+checks+"\n",StandardOpenOption.APPEND);
        } catch(Throwable t) {
            var out=new java.io.StringWriter(); t.printStackTrace(new java.io.PrintWriter(out));
            Files.writeString(RESULT,"FAILED "+checks+"\n"+out,StandardOpenOption.APPEND);
        }
        server.halt(false);
    }
}
