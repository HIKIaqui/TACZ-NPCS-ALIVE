package mod.cdv.tnpc.reputation;

import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.server.level.ServerPlayer;

/** Shared entry point for current checks and the upcoming dialogue/request/companion patches. */
public final class ReputationAccess {
    private ReputationAccess() {}
    public static long effective(AutoGunOperator npc,ServerPlayer player) {
        return ReputationData.get(player.server).effective(player.getUUID(),npc.getFactionId(),npc.getUUID());
    }
    public static boolean canTrade(AutoGunOperator npc,ServerPlayer player) {
        long value = effective(npc,player);
        return !ReputationConfig.settings().hostile(value) && value >= ReputationConfig.settings().tradeMinimum();
    }
}
