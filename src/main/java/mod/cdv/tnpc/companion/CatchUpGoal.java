package mod.cdv.tnpc.companion;
import java.util.EnumSet;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.world.entity.ai.goal.Goal;
/** Movement back to the leader wins over attack/chase, without overriding emergency escape. */
public final class CatchUpGoal extends Goal {
 private final AutoGunOperator npc;private final CompanionGoal movement;
 public CatchUpGoal(AutoGunOperator npc){this.npc=npc;movement=new CompanionGoal(npc);setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 @Override public boolean canUse(){return CompanionCombat.catchingUp(npc);}
 @Override public boolean canContinueToUse(){return canUse();}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void start(){movement.start();}
 @Override public void tick(){movement.tick();}
 @Override public void stop(){movement.stop();}
}
