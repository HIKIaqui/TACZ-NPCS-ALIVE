import java.io.InputStream;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Checks the shipped ADM binary contract used by the client-only mixin, without loading a GUI. */
public class AdmCompatibilitySmokeTest {
    public static void main(String[] args) throws Exception {
        ClassNode screen = read("net/aviel/dialogue/client/NpcDialogueScreen.class");
        boolean uuid = screen.fields.stream().anyMatch(f -> f.name.equals("npcUuid") && f.desc.equals("Ljava/util/UUID;"));
        boolean ctor = screen.methods.stream().anyMatch(m -> m.name.equals("<init>") && m.desc.equals(
            "(Ljava/util/UUID;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"));
        if (!uuid || !ctor) throw new AssertionError("ADM screen binary contract changed");
        ClassNode mixin = read("mod/cdv/tnpc/mixin/DialogueScreenMixin.class");
        if (!mixin.interfaces.contains("mod/cdv/tnpc/client/DialogueScreenAccessor"))
            throw new AssertionError("Screen view is missing");
        try (InputStream config = AdmCompatibilitySmokeTest.class.getClassLoader().getResourceAsStream("tacznpcs.mixins.json")) {
            String reserved = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(config, java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().get("package").getAsString().replace('.', '/') + "/";
            for (String contract : mixin.interfaces)
                if (contract.startsWith(reserved)) throw new AssertionError("Ordinary interface inside reserved mixin package: " + contract);
            Class<?> contract = Class.forName("mod.cdv.tnpc.client.DialogueScreenAccessor");
            if (!contract.isInterface()) throw new AssertionError("Screen contract must be an ordinary interface");
            if (AdmCompatibilitySmokeTest.class.getClassLoader().getResource("mod/cdv/tnpc/mixin/DialogueScreenAccessor.class") != null)
                throw new AssertionError("Stale interface remains in reserved mixin package");
        }
        var injected = mixin.methods.stream().filter(m -> m.name.equals("tnpc$rememberFile")).findFirst().orElseThrow();
        if (!injected.desc.equals("(Ljava/util/UUID;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"))
            throw new AssertionError("Constructor hook signature does not match ADM");
        for (String getter : new String[] {"tnpc$npcUuid", "tnpc$dialogueFile"})
            if (mixin.methods.stream().noneMatch(m -> m.name.equals(getter))) throw new AssertionError("Missing " + getter);
        System.out.println("PASS: ADM 0.8.5 screen field, constructor, client hook and reserved-package regression checks");
    }
    private static ClassNode read(String name) throws Exception {
        try (InputStream input = AdmCompatibilitySmokeTest.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new AssertionError("Missing binary " + name);
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_CODE); return node;
        }
    }
}
