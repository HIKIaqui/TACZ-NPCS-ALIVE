package mod.cdv.tnpc.mixin;

import java.util.UUID;
import mod.cdv.tnpc.client.DialogueScreenAccessor;
import net.aviel.dialogue.client.NpcDialogueScreen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ADM 0.8.5 discards its dialogueFile constructor argument and has no screen lifecycle API. */
@Mixin(value = NpcDialogueScreen.class, remap = false)
public abstract class DialogueScreenMixin implements DialogueScreenAccessor {
    @Shadow @Final private UUID npcUuid;
    @Unique private String tnpc$dialogueFile;
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void tnpc$rememberFile(UUID npc, String name, String file, String start, String json, CallbackInfo ci) {
        tnpc$dialogueFile = file;
    }
    @Override public UUID tnpc$npcUuid() { return npcUuid; }
    @Override public String tnpc$dialogueFile() { return tnpc$dialogueFile; }
}
