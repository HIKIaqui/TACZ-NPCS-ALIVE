// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.client;

import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import java.util.Map;
import mod.cdv.tnpc.client.FakePlayer;
import mod.cdv.tnpc.client.render.AutoGunOperatorRenderer;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.client.player.AbstractClientPlayer;

public final class FakePlayerPlayerAnimatorEvent {
    public static void animateTick() {
        AutoGunOperatorRenderer.fakePlayerCache.entrySet().removeIf(entry -> ((AutoGunOperator)((entry.getKey()))).isRemoved());
        for (Map.Entry<AutoGunOperator, FakePlayer> set : AutoGunOperatorRenderer.fakePlayerCache.entrySet()) {
            FakePlayer plr = set.getValue();
            double dx = plr.link.getX() - plr.link.xo;
            double dz = plr.link.getZ() - plr.link.zo;
            float movement = (float)Math.sqrt(dx * dx + dz * dz) * 4.0f;
            movement = Math.min(movement, 1.0f);
            plr.walkAnimation.update(movement, 0.2f);
            PlayerAnimationAccess.getPlayerAnimLayer((AbstractClientPlayer)plr).tick();
        }
    }
}

