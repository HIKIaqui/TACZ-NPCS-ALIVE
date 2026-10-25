// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.entity;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.shooter.LivingEntityAim;
import com.tacz.guns.entity.shooter.LivingEntityAmmoCheck;
import com.tacz.guns.entity.shooter.LivingEntityBolt;
import com.tacz.guns.entity.shooter.LivingEntityCrawl;
import com.tacz.guns.entity.shooter.LivingEntityDrawGun;
import com.tacz.guns.entity.shooter.LivingEntityFireSelect;
import com.tacz.guns.entity.shooter.LivingEntityMelee;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.LivingEntitySpeedModifier;
import com.tacz.guns.entity.shooter.LivingEntitySprint;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.item.ModernKineticGunItem;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.attachment.MeleeData;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunDefaultMeleeData;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import mod.cdv.tnpc.data.NPCTemplate;
import mod.cdv.tnpc.data.TemplateDataManager;
import mod.cdv.tnpc.entity.goal.AlertAlliesGoal;
import mod.cdv.tnpc.entity.goal.RestAtPoiGoal;
import mod.cdv.tnpc.entity.goal.AlertEnemiesGoal;
import mod.cdv.tnpc.entity.goal.FireAtNearestTargetGoal;
import mod.cdv.tnpc.network.AnimationPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class AutoGunOperator
extends PathfinderMob
implements IGunOperator {
    public static final EntityType<AutoGunOperator> NPC_TYPE = EntityType.Builder.of(AutoGunOperator::new, (MobCategory)MobCategory.MISC).sized(0.75f, 1.85f).build("npc");
    public FireAtNearestTargetGoal<AutoGunOperator> fireAtNearestTargetGoal;
    private final mod.cdv.tnpc.entity.ai.DamageEscapeMemory damageEscape = new mod.cdv.tnpc.entity.ai.DamageEscapeMemory();
    private final mod.cdv.tnpc.entity.ai.NpcAwareness awareness = new mod.cdv.tnpc.entity.ai.NpcAwareness();
    private String templateId = "default";
    private boolean templateInitialized;
    private NPCTemplate template;
    private Set<EntityType<?>> hostileTypes;
    private int meleeCooldown;
    private static final EntityDataAccessor<CompoundTag> SYNCED_SKIN = SynchedEntityData.defineId(AutoGunOperator.class, (EntityDataSerializer)EntityDataSerializers.COMPOUND_TAG);
    private final LivingEntity tacz$shooter = this;
    public final ShooterDataHolder tacz$data = new ShooterDataHolder();
    private final LivingEntityDrawGun tacz$draw;
    private final LivingEntityAim tacz$aim;
    private final LivingEntityCrawl tacz$crawl;
    private final LivingEntityAmmoCheck tacz$ammoCheck;
    private final LivingEntityFireSelect tacz$fireSelect;
    private final LivingEntityMelee tacz$melee;
    private final LivingEntityShoot tacz$shoot;
    private final LivingEntityBolt tacz$bolt;
    private final LivingEntityReload tacz$reload;
    private final LivingEntitySpeedModifier tacz$speed;
    private final LivingEntitySprint tacz$sprint;
    private boolean drawn = false;
    private final mod.cdv.tnpc.offline.OfflineActions offlineActions = new mod.cdv.tnpc.offline.OfflineActions();
    public mod.cdv.tnpc.offline.OfflineActions getOfflineActions() { return this.offlineActions; }

    private final mod.cdv.tnpc.inventory.NpcInventory npcInventory = new mod.cdv.tnpc.inventory.NpcInventory(this);
    public mod.cdv.tnpc.inventory.NpcInventory getNpcInventory() { return npcInventory; }

    private final mod.cdv.tnpc.quest.NpcQuestState questState = new mod.cdv.tnpc.quest.NpcQuestState();
    public mod.cdv.tnpc.quest.NpcQuestState getQuestState() { return questState; }

    @Override public boolean removeWhenFarAway(double distance) { return false; }

    public AutoGunOperator(EntityType<? extends PathfinderMob> p_20966_, Level p_20967_) {
        super(p_20966_, p_20967_);
        this.setPathfindingMalus(PathType.WATER, 1.0f);
        this.setPathfindingMalus(PathType.WATER_BORDER, 1.0f);
        this.initialData();
        this.setPersistenceRequired();
        if (p_20967_ instanceof ServerLevel serverLevel) this.offlineActions.initialize(serverLevel);
        this.tacz$draw = new LivingEntityDrawGun(this.tacz$shooter, this.tacz$data);
        this.tacz$aim = new LivingEntityAim(this.tacz$shooter, this.tacz$data);
        this.tacz$crawl = new LivingEntityCrawl(this.tacz$shooter, this.tacz$data);
        this.tacz$ammoCheck = new LivingEntityAmmoCheck(this.tacz$shooter);
        this.tacz$fireSelect = new LivingEntityFireSelect(this.tacz$shooter, this.tacz$data);
        this.tacz$melee = new LivingEntityMelee(this.tacz$shooter, this.tacz$data, this.tacz$draw);
        this.tacz$shoot = new LivingEntityShoot(this.tacz$shooter, this.tacz$data, this.tacz$draw);
        this.tacz$bolt = new LivingEntityBolt(this.tacz$data, this.tacz$shooter, this.tacz$draw, this.tacz$shoot);
        this.tacz$reload = new LivingEntityReload(this.tacz$shooter, this.tacz$data, this.tacz$draw, this.tacz$shoot);
        this.tacz$speed = new LivingEntitySpeedModifier(this.tacz$shooter, this.tacz$data);
        this.tacz$sprint = new LivingEntitySprint(this.tacz$shooter, this.tacz$data);
    }

    public static AutoGunOperator create(Level level) {
        return new AutoGunOperator(NPC_TYPE, level);
    }

    public static AutoGunOperator create(Level level, String templateId) {
        CompoundTag tag = new CompoundTag();
        tag.putString("template", templateId);
        tag.putString("id", "tacznpcs:npc");
        return (AutoGunOperator)(EntityType.create((CompoundTag)tag, (Level)level).orElseThrow());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AutoGunOperator.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.fireAtNearestTargetGoal = new FireAtNearestTargetGoal<>(this, 1.0, 8.0f);

        // Movement and look locks belong to ONE selector. Lower numbers win.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new mod.cdv.tnpc.entity.goal.EscapeUnknownDamageGoal(this));
        this.goalSelector.addGoal(2, new AvoidWhileReloadingGoal(this, 12.0f, 1.25));
        this.goalSelector.addGoal(3, this.fireAtNearestTargetGoal);
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false) {
            @Override
            public boolean canUse() {
                return !fireAtNearestTargetGoal.isHoldingGun() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !fireAtNearestTargetGoal.isHoldingGun() && super.canContinueToUse();
            }

            @Override
            protected boolean canPerformAttack(LivingEntity entity) {
                return !fireAtNearestTargetGoal.isHoldingGun() && super.canPerformAttack(entity);
            }
        });
        this.goalSelector.addGoal(5, new mod.cdv.tnpc.entity.goal.ConversationGoal(this));
        this.goalSelector.addGoal(6, new RestAtPoiGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0) {
            @Override
            public boolean canUse() {
                return getTarget() == null && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return getTarget() == null && super.canContinueToUse();
            }

            @Override
            public void start() {
                super.start();
                this.mob.setSprinting(true);
            }

            @Override
            public void stop() {
                super.stop();
                this.mob.setSprinting(false);
            }
        });
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 15.0f));
        // This goal alerts nearby enemies; it does not select our own target.
        this.goalSelector.addGoal(10, new AlertEnemiesGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                LivingEntity attacker = this.mob.getLastHurtByMob();
                return attacker != null && attacker != AutoGunOperator.this
                    && !AutoGunOperator.this.isAlliedTo(attacker) && super.canUse();
            }
        });
        this.targetSelector.addGoal(2, new mod.cdv.tnpc.entity.goal.PerceptionTargetGoal(this));
        this.targetSelector.addGoal(3, new AlertAlliesGoal(this));
    }

    public double getDetectionRange() {
        return this.awareness.detectionRange(this.level().getGameTime(), this.getTarget() != null,
            this.getAttributeValue(Attributes.FOLLOW_RANGE), mod.cdv.tnpc.data.NpcBehaviorConfig.perceptionFor(this.templateId));
    }

    public boolean isOnAlert() {
        return mod.cdv.tnpc.data.NpcBehaviorConfig.perceptionFor(this.templateId).enabled()
            && this.awareness.isAlert(this.level().getGameTime(), this.getTarget() != null);
    }

    public boolean canBeginRest() {
        return this.awareness.canRest(this.level().getGameTime(), this.getTarget() != null,
            mod.cdv.tnpc.data.NpcBehaviorConfig.perceptionFor(this.templateId));
    }

    public mod.cdv.tnpc.entity.ai.DamageEscapeMemory getDamageEscapeMemory() { return this.damageEscape; }

    public void reactToUnknownDanger(net.minecraft.world.phys.Vec3 incident) {
        var settings = mod.cdv.tnpc.data.NpcBehaviorConfig.damageEscapeFor(this.templateId);
        if (this.level().isClientSide || !this.isAlive() || this.getTarget() != null || !settings.enabled()) return;
        this.damageEscape.remember(incident, this.level().getGameTime(), settings.durationTicks());
        this.becomeAlert();
    }

    public void beginResting() { this.awareness.beginRest(); }
    public void endResting() { this.awareness.endRest(); }
    public void becomeAlert() {
        this.awareness.alert(this.level().getGameTime(),
            mod.cdv.tnpc.data.NpcBehaviorConfig.perceptionFor(this.templateId).alertDurationTicks());
    }

    @Override
    public void setTarget(@javax.annotation.Nullable LivingEntity target) {
        LivingEntity before = this.getTarget();
        super.setTarget(target);
        if (!this.level().isClientSide && this.fireAtNearestTargetGoal != null && this.awareness != null && this.getTarget() != before)
            this.fireAtNearestTargetGoal.targetChanged(this.getTarget());
        // Use the actual target: NeoForge can replace/cancel the requested target.
        if (!this.level().isClientSide && this.awareness != null && this.getTarget() != null && this.getTarget() != before)
            this.becomeAlert();
        if (this.getTarget() != null && this.damageEscape != null) this.damageEscape.clear();
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean accepted = super.hurt(source, amount);
        if (accepted && !this.level().isClientSide) this.becomeAlert();
        return accepted;
    }

    public boolean isAlliedTo(LivingEntity other) {
        if (other == null || this.template == null) {
            return false;
        }
        if (other instanceof AutoGunOperator npc) {
            return mod.cdv.tnpc.data.NpcRelations.allied(this.templateId, this.relationshipTemplate(),
                npc.templateId, npc.relationshipTemplate(),
                mod.cdv.tnpc.data.NpcRelations.entityGroups(this.getType(), TemplateDataManager.FACTIONS));
        }
        if (this.isOpposedTo(other)) return false;
        for (String id : this.relationshipTemplate().aligned()) {
            if (AutoGunOperator.isValidMobCategory(id)) {
                MobCategory category = MobCategory.valueOf((String)id.toUpperCase());
                if (other.getType().getCategory() == category) {
                    return true;
                }
            }
            Set<EntityType<?>> faction = TemplateDataManager.FACTIONS.getOrDefault(id, TemplateDataManager.EMPTY_ENTITY);
            if (!faction.contains(other.getType())) continue;
            return true;
        }
        return false;
    }

    public boolean isOpposedTo(LivingEntity other) {
        if (other == null || this.template == null) {
            return false;
        }
        if (other instanceof AutoGunOperator) {
            AutoGunOperator au = (AutoGunOperator)other;
            return mod.cdv.tnpc.data.NpcRelations.opposed(this.templateId, this.relationshipTemplate(),
                au.templateId, au.relationshipTemplate(),
                mod.cdv.tnpc.data.NpcRelations.entityGroups(this.getType(), TemplateDataManager.FACTIONS));
        } else {
            for (String id : this.relationshipTemplate().opposed()) {
                Set<EntityType<?>> faction;
                if (AutoGunOperator.isValidMobCategory(id)) {
                    MobCategory category = MobCategory.valueOf((String)id.toUpperCase());
                    if (other.getType().getCategory() == category) {
                        return true;
                    }
                }
                if (!(faction = TemplateDataManager.FACTIONS.getOrDefault(id, TemplateDataManager.EMPTY_ENTITY)).contains(other.getType())) continue;
                return true;
            }
        }
        return false;
    }

    public NPCTemplate relationshipTemplate() {
        return TemplateDataManager.TEMPLATES.getOrDefault(this.templateId, this.template);
    }

    public static boolean isValidMobCategory(String category) {
        return switch (category.toUpperCase()) {
            case "MONSTER", "CREATURE", "AMBIENT", "AXOLOTLS", "UNDERGROUND_WATER_CREATURE", "WATER_CREATURE", "WATER_AMBIENT", "MISC" -> true;
            default -> false;
        };
    }

    public boolean hasMelee() {
        Item item = this.getMainHandItem().getItem();
        if (!(item instanceof IGun)) {
            return false;
        }
        IGun iGun = (IGun)item;
        ResourceLocation gunId = iGun.getGunId(this.getMainHandItem());
        ResourceLocation muzzleId = iGun.getAttachmentId(this.getMainHandItem(), AttachmentType.MUZZLE);
        MeleeData muzzleMeleeData = this.getMeleeData(muzzleId);
        if (muzzleMeleeData != null) {
            return true;
        }
        ResourceLocation stockId = iGun.getAttachmentId(this.getMainHandItem(), AttachmentType.STOCK);
        MeleeData stockMeleeData = this.getMeleeData(stockId);
        if (stockMeleeData != null) {
            return true;
        }
        Optional index = TimelessAPI.getCommonGunIndex((ResourceLocation)gunId);
        if (index.isPresent()) {
            GunDefaultMeleeData defaultMeleeData = ((CommonGunIndex)index.get()).getGunData().getMeleeData().getDefaultMeleeData();
            return defaultMeleeData != null;
        }
        return false;
    }

    @Nullable
    private MeleeData getMeleeData(ResourceLocation attachmentId) {
        if (DefaultAssets.isEmptyAttachmentId((ResourceLocation)attachmentId)) {
            return null;
        }
        return TimelessAPI.getCommonAttachmentIndex((ResourceLocation)attachmentId).map(index -> index.getData().getMeleeData()).orElse(null);
    }

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && !player.isShiftKeyDown()) {
            if (this.level().isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                net.minecraft.world.InteractionResult result = mod.cdv.tnpc.dialogue.NpcConversations.interact(this, serverPlayer);
                if (result.consumesAction()) return result;
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.fireAtNearestTargetGoal != null) this.fireAtNearestTargetGoal.observeTarget();
    }

    public void tick() {
        if (this.level() instanceof ServerLevel serverLevel) mod.cdv.tnpc.quest.NpcQuests.tick(this, serverLevel);
        if (this.level() instanceof ServerLevel serverLevel) this.offlineActions.tick(this, serverLevel);
        if (this.isRemoved()) return;
        if (!this.level().isClientSide && (this.getTarget() != null || this.isOnFire())) this.becomeAlert();
        super.tick();
        this.onTickServerSide();
        if (this.hasMelee()) {
            if (this.getTarget() != null && this.distanceToSqr((Entity)this.getTarget()) <= 4.0) {
                this.lookAt((Entity)this.getTarget(), 15.0f, 15.0f);
            }
            // Navigation is owned by movement goals, not by an animation cooldown.
            if (this.getMainHandItem().getItem() instanceof ModernKineticGunItem && this.tacz$melee.getMeleeCoolDown() == 0L && this.meleeCooldown == 0 && this.getTarget() != null && this.isWithinMeleeAttackRange(this.getTarget()) && this.getSensing().hasLineOfSight((Entity)this.getTarget())) {
                this.meleeCooldown = this.random.nextInt(15, 25);
                this.fireAtNearestTargetGoal.attackDelay = (int)((double)this.meleeCooldown * 1.3);
                if (this.getSynMeleeCoolDown() == 0L && this.isWithinMeleeAttackRange(this.getTarget())) {
                    this.melee();
                }
            }
            if (this.meleeCooldown != 0) {
                --this.meleeCooldown;
            }
        }
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        CompoundTag tag = new CompoundTag();
        tag.putString("texture", NPCTemplate.DEFAULT_SKIN.texture());
        tag.putBoolean("slim", NPCTemplate.DEFAULT_SKIN.slim());
        builder.define(SYNCED_SKIN, tag);
    }

    public ResourceLocation getPlayerSkin() {
        return ResourceLocation.parse((String)((CompoundTag)this.entityData.get(SYNCED_SKIN)).getString("texture"));
    }

    public boolean isSlim() {
        return ((CompoundTag)this.entityData.get(SYNCED_SKIN)).getBoolean("slim");
    }

    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.offlineActions.save(tag);
        tag.putLong(mod.cdv.tnpc.travel.NpcTravel.EPOCH, this.getPersistentData().getLong(mod.cdv.tnpc.travel.NpcTravel.EPOCH));
        tag.putLong(mod.cdv.tnpc.travel.NpcTravel.ARRIVAL_COOLDOWN, this.getPersistentData().getLong(mod.cdv.tnpc.travel.NpcTravel.ARRIVAL_COOLDOWN));
        this.questState.save(tag);
        this.npcInventory.save(tag, this.registryAccess());
        tag.putLong("tnpcAlertUntil", this.awareness.alertUntil());
        tag.putString("template", this.templateId);
        if (this.templateInitialized) {
            tag.putBoolean("templateInitialized", true);
        }
        tag.put("skin", (Tag)this.entityData.get(SYNCED_SKIN));
        if (this.level() instanceof ServerLevel serverLevel) mod.cdv.tnpc.travel.NpcTravel.saved(this, tag, serverLevel);
    }

    public void readAdditionalSaveData(CompoundTag tag) {
        Level level;
        super.readAdditionalSaveData(tag);
        this.getPersistentData().putLong(mod.cdv.tnpc.travel.NpcTravel.EPOCH, tag.getLong(mod.cdv.tnpc.travel.NpcTravel.EPOCH));
        this.getPersistentData().putLong(mod.cdv.tnpc.travel.NpcTravel.ARRIVAL_COOLDOWN, tag.getLong(mod.cdv.tnpc.travel.NpcTravel.ARRIVAL_COOLDOWN));
        this.npcInventory.load(tag, this.registryAccess());
        if (!this.level().isClientSide) mod.cdv.tnpc.quest.NpcQuests.load(this, tag);
        this.setPersistenceRequired();
        if (this.level() instanceof ServerLevel serverLevel) this.offlineActions.load(tag, serverLevel);
        this.awareness.restore(tag.getLong("tnpcAlertUntil"));
        String templateTag = tag.contains("template") ? tag.getString("template") : "default";
        this.template = TemplateDataManager.TEMPLATES.getOrDefault(templateTag, NPCTemplate.DEFAULT);
        this.templateId = templateTag;
        this.templateInitialized = true;
        if (!(tag.contains("templateInitialized") && tag.getBoolean("templateInitialized") || !((level = this.level()) instanceof ServerLevel))) {
            Level level2;
            ServerLevel sv = (ServerLevel)level;
            if (this.template.equipment() != null && (level2 = this.level()) instanceof ServerLevel) {
                ServerLevel svl = (ServerLevel)level2;
                this.equip(this.template.equipment(), new LootParams.Builder(svl).create(LootContextParamSet.builder().build()));
            }
            for (ItemStack itemStack : this.template.randomEquip(sv)) {
                ItemStack equipped = this.equipItemIfPossible(itemStack.copy());
                ItemStack extra = itemStack.copy(); extra.shrink(equipped.getCount());
                this.npcInventory.store(extra);
            }
            if (!this.template.names().isEmpty()) {
                this.setCustomName((Component)Component.literal((String)this.template.names().get(this.random.nextInt(this.template.names().size()))));
            }
        }
        if (tag.contains("skin")) {
            this.entityData.set(SYNCED_SKIN, tag.getCompound("skin"));
        } else {
            CompoundTag skin = new CompoundTag();
            NPCTemplate.SkinTemplate texture = this.template.texture().get(this.random.nextInt(0, this.template.texture().size()));
            skin.putString("texture", texture.texture());
            skin.putBoolean("slim", texture.slim());
            this.entityData.set(SYNCED_SKIN, skin);
        }
    }

    public int getMaxFallDistance() {
        return 3;
    }

    private boolean wantsEquipment(ItemStack stack) {
        if (!(stack.getItem() instanceof ModernKineticGunItem || stack.getItem() instanceof ArmorItem)) return false;
        var slot = this.getEquipmentSlotForItem(stack);
        return this.canHoldItem(stack) && this.canReplaceCurrentItem(stack, this.getItemBySlot(slot));
    }
    @Override public boolean wantsToPickUp(ItemStack stack) {
        return wantsEquipment(stack) || mod.cdv.tnpc.inventory.NpcInventoryConfig.forLoadout(getTemplateId()).interested(stack);
    }
    @Override public boolean canPickUpLoot() { return true; }
    @Override protected void pickUpItem(net.minecraft.world.entity.item.ItemEntity entity) {
        if (wantsEquipment(entity.getItem())) super.pickUpItem(entity);
        else if (mod.cdv.tnpc.inventory.NpcInventoryConfig.forLoadout(getTemplateId()).interested(entity.getItem())) {
            // Vanilla's nearby-item scan already checks pickup delay and mobGriefing.
            this.npcInventory.store(entity.getItem());
            this.onItemPickup(entity);
            this.take(entity, entity.getItem().getCount());
            entity.discard();
        }
    }
    @Override public ItemStack equipItemIfPossible(ItemStack stack) {
        var slot = this.getEquipmentSlotForItem(stack);
        ItemStack previous = this.getItemBySlot(slot);
        if (stack.isEmpty() || !this.canHoldItem(stack) || !this.canReplaceCurrentItem(stack, previous)) return ItemStack.EMPTY;
        ItemStack equipped = slot.limit(stack.copy());
        if (!previous.isEmpty()) this.npcInventory.store(previous);
        this.setItemSlotAndDropWhenKilled(slot, equipped);
        return equipped;
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean recentlyHit) {
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack stack = this.getItemBySlot(slot);
            if (!stack.isEmpty()) { this.setItemSlot(slot, ItemStack.EMPTY); this.spawnAtLocation(stack); }
        }
        for (ItemStack stack : this.npcInventory.drainCargo()) this.spawnAtLocation(stack);
        super.dropCustomDeathLoot(level, source, recentlyHit);
    }

    public GunTabType heldGunType() {
        ModernKineticGunItem gun;
        Object object = this.getMainHandItem().getItem();
        if (object instanceof ModernKineticGunItem && TimelessAPI.getCommonGunIndex((ResourceLocation)(gun = (ModernKineticGunItem)object).getGunId(this.getMainHandItem())).isPresent()) {
            return switch (((CommonGunIndex)TimelessAPI.getCommonGunIndex((ResourceLocation)gun.getGunId(this.getMainHandItem())).get()).getType()) {
                case "pistol" -> GunTabType.PISTOL;
                case "rifle" -> GunTabType.RIFLE;
                case "sniper" -> GunTabType.SNIPER;
                case "smg" -> GunTabType.SMG;
                case "rpg" -> GunTabType.RPG;
                case "shotgun" -> GunTabType.SHOTGUN;
                case "mg" -> GunTabType.MG;
                default -> throw new IllegalStateException("Unexpected value: " + ((CommonGunIndex)TimelessAPI.getCommonGunIndex((ResourceLocation)gun.getGunId(this.getMainHandItem())).get()).getType());
            };
        }
        return GunTabType.PISTOL;
    }

    @Nullable
    public static GunTabType heldGunType(ItemStack gunStack) {
        Object object = gunStack.getItem();
        if (object instanceof ModernKineticGunItem) {
            ModernKineticGunItem gun = (ModernKineticGunItem)object;
            switch (((CommonGunIndex)TimelessAPI.getCommonGunIndex((ResourceLocation)gun.getGunId(gunStack)).get()).getType()) {
                case "pistol": {
                    return GunTabType.PISTOL;
                }
                case "rifle": {
                    return GunTabType.RIFLE;
                }
                case "sniper": {
                    return GunTabType.SNIPER;
                }
                case "smg": {
                    return GunTabType.SMG;
                }
                case "rpg": {
                    return GunTabType.RPG;
                }
                case "shotgun": {
                    return GunTabType.SHOTGUN;
                }
                case "mg": {
                    return GunTabType.MG;
                }
            }
        }
        return null;
    }

    public String combatGunType() {
        GunTabType type = this.heldGunType();
        return type == null ? "default" : type.name().toLowerCase(java.util.Locale.ROOT);
    }

    public int getStateRangedCooldown() {
        return mod.cdv.tnpc.data.NpcBehaviorConfig.combatFor(this.templateId).weapon(combatGunType())
            .burstPauseTicks().sample(this.random::nextInt);
    }

    public int getStateBurst() {
        return mod.cdv.tnpc.data.NpcBehaviorConfig.combatFor(this.templateId).weapon(combatGunType())
            .burstShots().sample(this.random::nextInt);
    }

    protected void reduceAmmo(ItemStack currentGunItem, ModernKineticGunItem gunitem) {
        Bolt boltType = TimelessAPI.getCommonGunIndex((ResourceLocation)gunitem.getGunId(currentGunItem)).map(index -> index.getGunData().getBolt()).orElse(null);
        if (boltType != null) {
            if (boltType == Bolt.MANUAL_ACTION) {
                gunitem.setBulletInBarrel(currentGunItem, false);
            } else if (boltType == Bolt.CLOSED_BOLT) {
                if (gunitem.getCurrentAmmoCount(currentGunItem) > 0) {
                    gunitem.reduceCurrentAmmoCount(currentGunItem);
                } else {
                    gunitem.setBulletInBarrel(currentGunItem, false);
                }
            } else {
                gunitem.reduceCurrentAmmoCount(currentGunItem);
            }
        }
    }

    public void draw(Supplier<ItemStack> gunItemSupplier) {
        this.tacz$draw.draw(gunItemSupplier);
        this.drawn = true;
    }

    public void reload() {
        this.tacz$reload.reload();
        PacketDistributor.sendToPlayersTrackingEntity((Entity)this, (CustomPacketPayload)new AnimationPacket((byte) 0, this.getId()), (CustomPacketPayload[])new CustomPacketPayload[0]);
    }

    public void aim(boolean isAim) {
        this.tacz$aim.aim(isAim);
    }

    public void crawl(boolean isCrawl) {
        this.tacz$crawl.crawl(isCrawl);
        PacketDistributor.sendToPlayersTrackingEntity((Entity)this, (CustomPacketPayload)new AnimationPacket((byte) 2, this.getId()), (CustomPacketPayload[])new CustomPacketPayload[0]);
    }

    public void updateCacheProperty(AttachmentCacheProperty cacheProperty) {
        this.tacz$data.cacheProperty = cacheProperty;
    }

    @Nullable
    public AttachmentCacheProperty getCacheProperty() {
        return this.tacz$data.cacheProperty;
    }

    public void fireSelect() {
        this.tacz$fireSelect.fireSelect();
    }

    public void zoom() {
        this.tacz$aim.zoom();
    }

    public long getSynShootCoolDown() {
        return (Long)ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.getValue(this.tacz$shooter);
    }

    public long getSynMeleeCoolDown() {
        return (Long)ModSyncedEntityData.MELEE_COOL_DOWN_KEY.getValue(this.tacz$shooter);
    }

    public long getSynDrawCoolDown() {
        return (Long)ModSyncedEntityData.DRAW_COOL_DOWN_KEY.getValue(this.tacz$shooter);
    }

    public boolean getSynIsBolting() {
        return (Boolean)ModSyncedEntityData.IS_BOLTING_KEY.getValue(this.tacz$shooter);
    }

    public ReloadState getSynReloadState() {
        return (ReloadState)ModSyncedEntityData.RELOAD_STATE_KEY.getValue(this.tacz$shooter);
    }

    public float getSynAimingProgress() {
        return ((Float)ModSyncedEntityData.AIMING_PROGRESS_KEY.getValue(this.tacz$shooter)).floatValue();
    }

    public float getSynSprintTime() {
        return ((Float)ModSyncedEntityData.SPRINT_TIME_KEY.getValue(this.tacz$shooter)).floatValue();
    }

    public boolean getSynIsAiming() {
        return (Boolean)ModSyncedEntityData.IS_AIMING_KEY.getValue(this.tacz$shooter);
    }

    public void initialData() {
        this.tacz$data.initialData();
        AttachmentPropertyManager.postChangeEvent((LivingEntity)this.tacz$shooter, (ItemStack)this.tacz$shooter.getMainHandItem());
    }

    public void bolt() {
        this.tacz$bolt.bolt();
    }

    public void cancelReload() {
        this.tacz$reload.cancelReload();
    }

    public void melee() {
        this.tacz$melee.melee();
        PacketDistributor.sendToPlayersTrackingEntity((Entity)this, (CustomPacketPayload)new AnimationPacket((byte) 1, this.getId()), (CustomPacketPayload[])new CustomPacketPayload[0]);
    }

    public ShootResult shoot(Supplier<Float> pitch, Supplier<Float> yaw) {
        return this.shoot(pitch, yaw, System.currentTimeMillis() - this.tacz$data.baseTimestamp);
    }

    public ShootResult shoot(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp) {
        return this.tacz$shoot.shoot(pitch, yaw, timestamp);
    }

    public boolean needCheckAmmo() {
        return false;
    }

    public boolean consumesAmmoOrNot() {
        return this.tacz$ammoCheck.consumesAmmoOrNot();
    }

    public boolean getProcessedSprintStatus(boolean sprint) {
        return this.tacz$sprint.getProcessedSprintStatus(sprint);
    }

    public ShooterDataHolder getDataHolder() {
        return this.tacz$data;
    }

    public boolean nextBulletIsTracer(int tracerCountInterval) {
        ++this.tacz$data.shootCount;
        if (tracerCountInterval == -1) {
            return false;
        }
        return this.tacz$data.shootCount % (tracerCountInterval + 1) == 0;
    }

    private void onTickServerSide() {
        if (!this.level().isClientSide()) {
            Item item = this.getMainHandItem().getItem();
            if (item instanceof ModernKineticGunItem) {
                Optional gunIndexOptional;
                ModernKineticGunItem gun = (ModernKineticGunItem)item;
                if (!this.drawn) {
                    this.draw(() -> ((AutoGunOperator)this).getMainHandItem());
                }
                ItemStack gunItem = this.getMainHandItem();
                ResourceLocation gunId = gun.getGunId(gunItem);
                IGun iGun = IGun.getIGunOrNull((ItemStack)gunItem);
                AutoGunOperator shooter = this;
                if (iGun != null && (gunIndexOptional = TimelessAPI.getCommonGunIndex((ResourceLocation)gunId)).isPresent()) {
                    CommonGunIndex gunIndex = (CommonGunIndex)gunIndexOptional.get();
                    BulletData bulletData = gunIndex.getBulletData();
                    GunData gunData = gunIndex.getGunData();
                    AttachmentCacheProperty property = new AttachmentCacheProperty();
                    property.eval(this.getMainHandItem(), gunData);
                    this.updateCacheProperty(property);
                }
            }
            this.bolt();
            ReloadState reloadState = this.tacz$reload.tickReloadState();
            this.tacz$aim.tickAimingProgress();
            this.tacz$aim.tickSprint();
            this.tacz$crawl.tickCrawling();
            this.tacz$bolt.tickBolt();
            this.tacz$melee.scheduleTickMelee();
            this.tacz$speed.updateSpeedModifier();
            this.tacz$shooter.setSprinting(this.getProcessedSprintStatus(this.tacz$shooter.isSprinting()));
            ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.setValue(this.tacz$shooter, this.tacz$shoot.getShootCoolDown());
            ModSyncedEntityData.MELEE_COOL_DOWN_KEY.setValue(this.tacz$shooter, this.tacz$melee.getMeleeCoolDown());
            ModSyncedEntityData.DRAW_COOL_DOWN_KEY.setValue(this.tacz$shooter, this.tacz$draw.getDrawCoolDown());
            ModSyncedEntityData.IS_BOLTING_KEY.setValue(this.tacz$shooter, this.tacz$data.isBolting);
            ModSyncedEntityData.RELOAD_STATE_KEY.setValue(this.tacz$shooter, reloadState);
            ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(this.tacz$shooter, Float.valueOf(this.tacz$data.aimingProgress));
            ModSyncedEntityData.IS_AIMING_KEY.setValue(this.tacz$shooter, this.tacz$data.isAiming);
            ModSyncedEntityData.SPRINT_TIME_KEY.setValue(this.tacz$shooter, Float.valueOf(this.tacz$data.sprintTimeS));
        }
    }

    public boolean shouldShowName() {
        return false;
    }

    public String getTemplateId() {
        return this.templateId;
    }

    public NPCTemplate getTemplate() {
        return this.template;
    }

    public class AvoidWhileReloadingGoal extends AvoidEntityGoal<LivingEntity> {
        public AvoidWhileReloadingGoal(AutoGunOperator mob, float maxDistance, double speedModifier) {
            // Retreat only from our current threat, never a bystander or an ally.
            super(mob, LivingEntity.class, maxDistance, speedModifier, speedModifier,
                candidate -> candidate == mob.getTarget() && !mob.isAlliedTo(candidate));
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        private boolean shouldRetreat() {
            LivingEntity target = AutoGunOperator.this.getTarget();
            if (target == null || !target.isAlive() || target.isSpectator()
                || !AutoGunOperator.this.canAttack(target) || AutoGunOperator.this.isAlliedTo(target)
                || !AutoGunOperator.this.fireAtNearestTargetGoal.isHoldingGun()) {
                return false;
            }
            boolean reloading = AutoGunOperator.this.getDataHolder().reloadStateType
                != ReloadState.StateType.NOT_RELOADING;
            boolean bolting = AutoGunOperator.this.getSynIsBolting();
            boolean tooClose = AutoGunOperator.this.distanceToSqr(target) <= 4.0
                && !AutoGunOperator.this.hasMelee();
            return reloading || bolting || tooClose;
        }

        @Override
        public boolean canUse() {
            return shouldRetreat() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.toAvoid == AutoGunOperator.this.getTarget()
                && shouldRetreat() && super.canContinueToUse();
        }

        @Override
        public void tick() {
            super.tick();
            if (this.toAvoid != null) {
                AutoGunOperator.this.getLookControl().setLookAt(this.toAvoid, 30.0f, 30.0f);
            }
        }

        @Override
        public void stop() {
            super.stop();
            AutoGunOperator.this.getNavigation().stop();
        }
    }
}
