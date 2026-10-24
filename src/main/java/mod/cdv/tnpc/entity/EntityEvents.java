// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.entity;

import mod.cdv.tnpc.entity.AutoGunOperator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class EntityEvents {
    public static void init(IEventBus modBus) {
        modBus.addListener(EntityEvents::registerAttributes);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(AutoGunOperator.NPC_TYPE, AutoGunOperator.createAttributes().build());
    }
}

