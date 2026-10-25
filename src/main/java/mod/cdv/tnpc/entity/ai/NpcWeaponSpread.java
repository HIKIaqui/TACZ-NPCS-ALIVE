package mod.cdv.tnpc.entity.ai;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import mod.cdv.tnpc.entity.AutoGunOperator;
import mod.cdv.tnpc.quest.NpcQuests;
import mod.cdv.tnpc.quest.QuestConfig;

/** Scope the experience multiplier to this NPC's native shot; never mutate the gun item. */
public final class NpcWeaponSpread {
    private NpcWeaponSpread() {}
    public static Map<InaccuracyType, Float> scaled(Map<InaccuracyType, Float> original, double factor) {
        var result = new EnumMap<InaccuracyType, Float>(InaccuracyType.class);
        original.forEach((type, value) -> result.put(type, (float)(value * factor)));
        return result;
    }
    public static ShootResult shoot(AutoGunOperator npc, Supplier<ShootResult> action) {
        var cache = npc.getDataHolder().cacheProperty;
        if (npc.level().isClientSide || cache == null) return action.get();
        double factor = QuestConfig.settings().progression().weaponSpread().multiplier(NpcQuests.level(npc));
        return scoped(cache, factor, action);
    }
    public static ShootResult scoped(AttachmentCacheProperty cache, double factor, Supplier<ShootResult> action) {
        var normal = cache.getCache(GunProperties.INACCURACY);
        var aimed = cache.getCache(GunProperties.AIM_INACCURACY);
        try {
            cache.setCache(GunProperties.INACCURACY, scaled(normal, factor));
            cache.setCache(GunProperties.AIM_INACCURACY, scaled(aimed, factor));
            return action.get();
        } finally {
            cache.setCache(GunProperties.INACCURACY, normal);
            cache.setCache(GunProperties.AIM_INACCURACY, aimed);
        }
    }
}
