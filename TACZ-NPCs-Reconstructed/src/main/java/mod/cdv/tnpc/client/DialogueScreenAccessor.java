package mod.cdv.tnpc.client;

import java.util.UUID;

/** Implemented only by the client mixin for ADM's dialogue screen. */
public interface DialogueScreenAccessor {
    UUID tnpc$npcUuid();
    String tnpc$dialogueFile();
}
