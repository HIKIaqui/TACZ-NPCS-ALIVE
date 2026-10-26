package mod.cdv.tnpc.companion;
import java.util.EnumSet;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.world.entity.ai.goal.Goal;

/** Holds MOVE during peaceful following/waiting, below combat, escape, reload and conversation. */
public final class CompanionGoal extends Goal {
    private final AutoGunOperator npc;private int delay;
    public CompanionGoal(AutoGunOperator npc){this.npc=npc;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse(){return Companions.active(npc) && npc.getTarget()==null;}
    @Override public boolean canContinueToUse(){return canUse();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void start(){delay=0;npc.endResting();npc.getNavigation().stop();}
    @Override public void stop(){npc.getNavigation().stop();npc.setSprinting(false);}
    @Override public void tick(){
        if(delay-- > 0)return;delay=9;
        if(Companions.waiting(npc)) {
            var spot=Companions.anchor(npc);npc.setSprinting(false);
            if(npc.position().distanceToSqr(spot)>2.25)npc.getNavigation().moveTo(spot.x,spot.y,spot.z,1.0);else npc.getNavigation().stop();
            return;
        }
        var p=Companions.leader(npc);
        if(p==null || !p.isAlive() || p.isSpectator() || p.level()!=npc.level()){npc.getNavigation().stop();npc.setSprinting(false);return;}
        npc.getLookControl().setLookAt(p,30,30);double distance=npc.distanceToSqr(p);
        npc.setSprinting(distance>64);
        if(distance>9)npc.getNavigation().moveTo(p,1.1);else npc.getNavigation().stop();
    }
}
