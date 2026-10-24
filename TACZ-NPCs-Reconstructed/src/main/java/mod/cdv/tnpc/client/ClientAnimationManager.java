// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import mod.cdv.tnpc.client.FakePlayer;
import mod.cdv.tnpc.client.render.AutoGunOperatorRenderer;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

public final class ClientAnimationManager {
    public static void reload(int entityId) {
        FakePlayer plr = AutoGunOperatorRenderer.fakePlayerCache.get(Minecraft.getInstance().level.getEntity(entityId));
        if (plr != null) {
            TimelessAPI.getGunDisplay((ItemStack)plr.getMainHandItem()).ifPresent(index -> {
                if (!plr.isSwimming() && plr.getPose() == Pose.SWIMMING) {
                    AnimationManager.playOnceAnimation((AbstractClientPlayer)plr, (GunDisplayInstance)index, (ResourceLocation)PlayerAnimatorCompat.ONCE_UPPER_ANIMATION, (String)"lie_reload");
                } else {
                    AnimationManager.playOnceAnimation((AbstractClientPlayer)plr, (GunDisplayInstance)index, (ResourceLocation)PlayerAnimatorCompat.ONCE_UPPER_ANIMATION, (String)"reload_upper");
                }
            });
        }
    }

    public static void melee(int entityId) {
        FakePlayer plr = AutoGunOperatorRenderer.fakePlayerCache.get(Minecraft.getInstance().level.getEntity(entityId));
        if (plr != null) {
            TimelessAPI.getGunDisplay((ItemStack)plr.getMainHandItem()).ifPresent(index -> {
                int randomIndex = plr.getRandom().nextInt(3);
                String animationName = switch (randomIndex) {
                    case 0 -> "melee_upper";
                    case 1 -> "melee_2_upper";
                    default -> "melee_3_upper";
                };
                AnimationManager.playOnceAnimation((AbstractClientPlayer)plr, (GunDisplayInstance)index, (ResourceLocation)PlayerAnimatorCompat.ONCE_UPPER_ANIMATION, (String)animationName);
            });
        }
    }

    public static void die(int entityId) {
        Entity ent = Minecraft.getInstance().level.getEntity(entityId);
        if (ent instanceof AutoGunOperator) {
            AutoGunOperator au = (AutoGunOperator)ent;
            FakePlayer plr = AutoGunOperatorRenderer.fakePlayerCache.get(ent);
            if (plr != null) {
                // empty if block
            }
        }
    }
}

