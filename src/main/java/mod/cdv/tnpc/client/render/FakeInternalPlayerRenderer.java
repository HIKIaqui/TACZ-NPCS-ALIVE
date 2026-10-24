// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.client.render;

import mod.cdv.tnpc.client.FakePlayer;
import mod.cdv.tnpc.client.render.AutoGunOperatorRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

public class FakeInternalPlayerRenderer
extends PlayerRenderer {
    final AutoGunOperatorRenderer link;

    public FakeInternalPlayerRenderer(EntityRendererProvider.Context p_174557_, boolean p_174558_, AutoGunOperatorRenderer link) {
        super(p_174557_, p_174558_);
        this.link = link;
    }

    protected boolean shouldShowName(AbstractClientPlayer p_115333_) {
        if (p_115333_ instanceof FakePlayer) {
            FakePlayer plr = (FakePlayer)p_115333_;
            return this.link.shouldShowName(plr.link);
        }
        return false;
    }
}

