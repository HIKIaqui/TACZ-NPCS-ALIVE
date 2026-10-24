// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.client;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import java.util.function.Supplier;
import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FakePlayer
extends RemotePlayer
implements IGunOperator {
    public final AutoGunOperator link;
    private PlayerSkin skin = null;

    public FakePlayer(ClientLevel p_252213_, GameProfile p_250471_, AutoGunOperator link) {
        super(p_252213_, p_250471_);
        this.link = link;
    }

    public boolean isModelPartShown(PlayerModelPart p_36171_) {
        return true;
    }

    @NotNull
    public PlayerSkin getSkin() {
        if (this.skin == null) {
            this.skin = new PlayerSkin(this.link.getPlayerSkin(), null, null, null, this.link.isSlim() ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, false);
        }
        return this.skin;
    }

    public boolean shouldShowName() {
        return this.link.shouldShowName();
    }

    public Component getDisplayName() {
        return this.link.getDisplayName();
    }

    public Component getName() {
        return this.link.getDisplayName();
    }

    public long getSynShootCoolDown() {
        return this.link.getSynShootCoolDown();
    }

    public long getSynMeleeCoolDown() {
        return this.link.getSynMeleeCoolDown();
    }

    public long getSynDrawCoolDown() {
        return this.link.getSynDrawCoolDown();
    }

    public boolean getSynIsBolting() {
        return this.link.getSynIsBolting();
    }

    public ReloadState getSynReloadState() {
        return this.link.getSynReloadState();
    }

    public float getSynAimingProgress() {
        return this.link.getSynAimingProgress();
    }

    public boolean getSynIsAiming() {
        return this.link.getSynIsAiming();
    }

    public float getSynSprintTime() {
        return this.link.getSynSprintTime();
    }

    public void initialData() {
    }

    public void draw(Supplier<ItemStack> itemStackSupplier) {
    }

    public void bolt() {
    }

    public void reload() {
    }

    public void cancelReload() {
    }

    public void fireSelect() {
    }

    public void zoom() {
    }

    public void melee() {
    }

    public ShootResult shoot(Supplier<Float> pitch, Supplier<Float> yaw) {
        return null;
    }

    public ShootResult shoot(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp) {
        return null;
    }

    public boolean needCheckAmmo() {
        return this.link.needCheckAmmo();
    }

    public boolean consumesAmmoOrNot() {
        return this.link.consumesAmmoOrNot();
    }

    public boolean getProcessedSprintStatus(boolean sprint) {
        return this.link.getProcessedSprintStatus(sprint);
    }

    public void aim(boolean isAim) {
    }

    public void crawl(boolean isCrawl) {
    }

    public void updateCacheProperty(AttachmentCacheProperty cacheProperty) {
    }

    @Nullable
    public AttachmentCacheProperty getCacheProperty() {
        return this.link.getCacheProperty();
    }

    public ShooterDataHolder getDataHolder() {
        return this.link.getDataHolder();
    }

    public boolean nextBulletIsTracer(int tracerCountInterval) {
        return this.link.nextBulletIsTracer(tracerCountInterval);
    }
}

