package mod.cdv.tnpc.entity.goal;

import java.util.EnumSet;
import mod.cdv.tnpc.dialogue.NpcConversations;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

/** Only occupies movement/look while calm; target selection and emergency goals remain active. */
public final class ConversationGoal extends Goal {
    private final AutoGunOperator npc;
    public ConversationGoal(AutoGunOperator npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse() { return NpcConversations.partner(npc) != null; }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { npc.getNavigation().stop(); }
    @Override public void tick() {
        ServerPlayer player = NpcConversations.partner(npc);
        if (player != null) { npc.getNavigation().stop(); npc.getLookControl().setLookAt(player, 30, 30); }
    }
}
