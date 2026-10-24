package mod.cdv.tnpc.campfire;

import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CampfirePois {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("tacznpcs", "campfire");
    public static final DeferredRegister<PoiType> TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, "tacznpcs");
    public static final DeferredHolder<PoiType, PoiType> CAMPFIRE = TYPES.register("campfire",
        () -> new PoiType(Set.copyOf(Blocks.CAMPFIRE.getStateDefinition().getPossibleStates()), 12, 1));
    private CampfirePois() {}
}
