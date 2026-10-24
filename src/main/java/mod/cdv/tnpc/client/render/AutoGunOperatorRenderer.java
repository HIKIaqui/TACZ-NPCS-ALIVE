// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.client.render;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import mod.cdv.tnpc.client.FakePlayer;
import mod.cdv.tnpc.client.render.AutoGunOperatorModel;
import mod.cdv.tnpc.client.render.FakeInternalPlayerRenderer;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.NotNull;

public class AutoGunOperatorRenderer
extends HumanoidMobRenderer<AutoGunOperator, AutoGunOperatorModel> {
    public static HashMap<AutoGunOperator, FakePlayer> fakePlayerCache = new HashMap();
    private final FakeInternalPlayerRenderer slimRenderer;
    private final FakeInternalPlayerRenderer wideRenderer;

    public AutoGunOperatorRenderer(EntityRendererProvider.Context context) {
        super(context, new AutoGunOperatorModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.wideRenderer = new FakeInternalPlayerRenderer(context, false, this);
        this.slimRenderer = new FakeInternalPlayerRenderer(context, true, this);
        this.addLayer((RenderLayer)new HumanoidArmorLayer((RenderLayerParent)this, (HumanoidModel)new HumanoidArmorModel(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), (HumanoidModel)new HumanoidArmorModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
        this.addLayer((RenderLayer)new PlayerItemInHandLayer((RenderLayerParent)this, context.getItemInHandRenderer()));
        this.addLayer((RenderLayer)new ArrowLayer(context, (LivingEntityRenderer)this));
        this.addLayer((RenderLayer)new CustomHeadLayer((RenderLayerParent)this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer((RenderLayer)new ElytraLayer((RenderLayerParent)this, context.getModelSet()));
        this.addLayer((RenderLayer)new ParrotOnShoulderLayer((RenderLayerParent)this, context.getModelSet()));
        this.addLayer((RenderLayer)new BeeStingerLayer((LivingEntityRenderer)this));
    }

    public void render(@NotNull AutoGunOperator op, float i1, float i2, PoseStack poseStack, MultiBufferSource bufferSource, int i3) {
        FakePlayer plr = fakePlayerCache.computeIfAbsent(op, e -> new FakePlayer(Minecraft.getInstance().level, new GameProfile(op.getUUID(), "npc"), op));
        plr.tickCount = op.tickCount;
        plr.setPos(op.getX(), op.getY(), op.getZ());
        plr.xo = op.xo;
        plr.yo = op.yo;
        plr.zo = op.zo;
        plr.setDeltaMovement(op.getDeltaMovement());
        plr.setYRot(op.getYRot());
        plr.yRotO = op.yRotO;
        plr.setXRot(op.getXRot());
        plr.xRotO = op.xRotO;
        plr.yHeadRot = op.yHeadRot;
        plr.yHeadRotO = op.yHeadRotO;
        plr.yBodyRot = op.yBodyRot;
        plr.yBodyRotO = op.yBodyRotO;
        plr.setPose(op.getPose());
        plr.setSprinting(op.isSprinting());
        plr.setShiftKeyDown(op.isCrouching());
        plr.hurtTime = op.hurtTime;
        plr.deathTime = op.deathTime;
        plr.swinging = op.swinging;
        plr.swingingArm = op.swingingArm;
        plr.swingTime = op.swingTime;
        plr.setHealth(op.getHealth());
        for (EquipmentSlot value : EquipmentSlot.values()) {
            plr.setItemSlot(value, op.getItemBySlot(value));
        }
        if (plr.link.isSlim()) {
            this.slimRenderer.render((AbstractClientPlayer)plr, i1, i2, poseStack, bufferSource, i3);
        } else {
            this.wideRenderer.render((AbstractClientPlayer)plr, i1, i2, poseStack, bufferSource, i3);
        }
    }

    protected boolean shouldShowName(AutoGunOperator op) {
        return super.shouldShowName(op) && !op.getName().getString().isBlank();
    }

    public ResourceLocation getTextureLocation(AutoGunOperator autoGunOperator) {
        return autoGunOperator.getPlayerSkin();
    }
}

