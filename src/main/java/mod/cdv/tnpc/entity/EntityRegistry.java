// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.entity;

import mod.cdv.tnpc.entity.AutoGunOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class EntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create((Registry)BuiltInRegistries.ENTITY_TYPE, (String)"tacznpcs");
    public static DeferredHolder<EntityType<?>, EntityType<AutoGunOperator>> NPC_REGISTER = ENTITY_TYPES.register("npc", () -> AutoGunOperator.NPC_TYPE);
}

