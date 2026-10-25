// Reconstructed from TacZ: NPCs 2.1.0 using CFR 0.152. Original author: Corbin Smith.
package mod.cdv.tnpc.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import mod.cdv.tnpc.data.NPCTemplate;
import mod.cdv.tnpc.data.StructureTemplate;
import mod.cdv.tnpc.data.TemplateException;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentTable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

public final class TemplateDataManager {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final HashMap<String, NPCTemplate> TEMPLATES = new HashMap();
    public static final HashMap<String, Set<EntityType<?>>> FACTIONS = new HashMap();
    public static final HashMap<String, ArrayList<StructureTemplate>> STRUCTURES = new HashMap();
    public static final String DEFAULT_FACTIONS = "{\n    \"players\": [\"minecraft:player\"],\n    \"villagers\": [\"minecraft:villager\", \"minecraft:wandering_trader\", \"minecraft:iron_golem\"],\n    \"illagers\": [\"minecraft:pillager\", \"minecraft:evoker\", \"minecraft:vindicator\", \"minecraft:illusioner\", \"minecraft:ravager\", \"minecraft:vex\"]\n}\n";
    public static final String DEFAULT_LOADOUTS = """
{
  "illagers": {
    "equipment": {
      "table": "tacznpcs:npc/illager",
      "dropChances": {
        "mainhand": 1,
        "head": 0.2,
        "chest": 0.2,
        "legs": 0.2,
        "feet": 0.2
      }
    },
    "skin": [
      {
        "id": "tacznpcs:entity/illager1.png"
      },
      {
        "id": "tacznpcs:entity/illager1.png"
      },
      {
        "id": "tacznpcs:entity/illager1.png"
      },
      {
        "id": "tacznpcs:entity/illager2.png"
      }
    ],
    "faction": "illagers",
    "relations": {
      "illagers": 50,
      "monster": 50,
      "villagers": -100
    }
  },
  "villagers": {
    "equipment": {
      "table": "tacznpcs:npc/villager",
      "dropChances": {
        "mainhand": 1,
        "head": 0.2,
        "chest": 0.2,
        "legs": 0.2,
        "feet": 0.2
      }
    },
    "skin": [
      {
        "id": "tacznpcs:entity/villager2.png"
      },
      {
        "id": "tacznpcs:entity/villager2.png"
      },
      {
        "id": "tacznpcs:entity/villager2.png"
      },
      {
        "id": "tacznpcs:entity/villager1.png"
      }
    ],
    "faction": "villagers",
    "relations": {
      "villagers": 50,
      "illagers": -100,
      "monster": -100
    }
  },
  "default": {
    "primary": "%tacz:glock_17",
    "skin": {
      "id": "tacznpcs:entity/default.png"
    },
    "faction": "default",
    "relations": {
      "monster": -100
    }
  }
}
        """;
    public static final String DEFAULT_STRUCTURES = "{\n \"minecraft:pillager_outpost\": [\n   {\n     \"entities\": [\n       \"minecraft:pillager\"\n     ],\n     \"loadout\": \"illagers\",\n     \"chance\": 0.25\n   }\n ],\n \"minecraft:mansion\": [\n   {\n     \"entities\": [\n       \"minecraft:pillager\",\n       \"minecraft:vindicator\"\n     ],\n     \"loadout\": \"illagers\",\n     \"chance\": 0.25\n   }\n ],\n \"minecraft:village_plains\": [\n   {\n     \"entities\": [\n       \"minecraft:villager\"\n     ],\n     \"loadout\": \"villagers\",\n     \"chance\": 0.05\n   },\n   {\n     \"entities\": [\n       \"minecraft:iron_golem\"\n     ],\n     \"loadout\": \"villagers\",\n     \"chance\": 0.5\n   }\n ],\n \"minecraft:village_taiga\": [\n   {\n     \"entities\": [\n       \"minecraft:villager\"\n     ],\n     \"loadout\": \"villagers\",\n     \"chance\": 0.05\n   },\n   {\n     \"entities\": [\n       \"minecraft:iron_golem\"\n     ],\n     \"loadout\": \"villagers\",\n     \"chance\": 0.5\n   }\n ],\n \"minecraft:village_savanna\": [\n   {\n     \"entities\": [\n       \"minecraft:villager\"\n     ],\n     \"loadout\": \"villagers\",\n     \"chance\": 0.05\n   },\n   {\n     \"entities\": [\n       \"minecraft:iron_golem\"\n     ],\n     \"loadout\": \"villagers\",\n     \"chance\": 0.5\n   }\n ],\n \"minecraft:village_desert\": [\n   {\n     \"entities\": [\n       \"minecraft:villager\"\n     ],\n     \"loadout\": \"villagers\",\n     \"chance\": 0.05\n   },\n   {\n     \"entities\": [\n       \"minecraft:iron_golem\"\n     ],\n     \"loadout\": \"villagers\",\n     \"chance\": 0.5\n   }\n ]\n}\n";
    public static final Set<String> EMPTY = Set.of();
    public static final ItemStack[] EMPTY_ITEM = new ItemStack[0];
    public static final Set<EntityType<?>> EMPTY_ENTITY = Set.of();

    public static boolean init(MinecraftServer server) {
        Path loadoutPath = server.getServerDirectory().resolve("config").resolve("tacznpc_loadouts.json");
        Path factionsPath = server.getServerDirectory().resolve("config").resolve("tacznpc_factions.json");
        Path structuresPath = server.getServerDirectory().resolve("config").resolve("tacznpc_structures.json");
        try {
            if (Files.notExists(loadoutPath, new LinkOption[0])) {
                Files.createFile(loadoutPath, new FileAttribute[0]);
                Files.writeString(loadoutPath, (CharSequence)DEFAULT_LOADOUTS, new OpenOption[0]);
            } else if (Files.readString(loadoutPath).isEmpty()) {
                Files.writeString(loadoutPath, (CharSequence)DEFAULT_LOADOUTS, new OpenOption[0]);
            }
            if (Files.notExists(factionsPath, new LinkOption[0])) {
                Files.createFile(factionsPath, new FileAttribute[0]);
                Files.writeString(factionsPath, (CharSequence)DEFAULT_FACTIONS, new OpenOption[0]);
            } else if (Files.readString(factionsPath).isEmpty()) {
                Files.writeString(factionsPath, (CharSequence)DEFAULT_FACTIONS, new OpenOption[0]);
            }
            if (Files.notExists(structuresPath, new LinkOption[0])) {
                Files.createFile(structuresPath, new FileAttribute[0]);
                Files.writeString(structuresPath, (CharSequence)DEFAULT_STRUCTURES, new OpenOption[0]);
            } else if (Files.readString(structuresPath).isEmpty()) {
                Files.writeString(structuresPath, (CharSequence)DEFAULT_STRUCTURES, new OpenOption[0]);
            }
            // Parse every relation-dependent file before replacing the live configuration.
            var templates = TemplateDataManager.loadTemplates(server, loadoutPath);
            var factions = TemplateDataManager.loadFactions(factionsPath);
            var structures = TemplateDataManager.loadStructures(structuresPath);
            templates.putIfAbsent("default", NPCTemplate.DEFAULT);
            TEMPLATES.clear(); TEMPLATES.putAll(templates);
            FACTIONS.clear(); FACTIONS.putAll(factions);
            STRUCTURES.clear(); STRUCTURES.putAll(structures);
            return true;
        }
        catch (IOException | RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().error("Could not load NPC templates/factions/structures; keeping previous settings. Replace aligned/opposed with faction and relations.", e);
            return false;
        }
    }

    private static Map<String, ArrayList<StructureTemplate>> loadStructures(Path structuresPath) throws FileNotFoundException {
        JsonObject root = (JsonObject)GSON.fromJson((Reader)new FileReader(structuresPath.toFile()), JsonObject.class);
        if (root == null) {
            throw new TemplateException("no JsonObject present in tacznpc_structures.json!");
        }
        HashMap<String, ArrayList<StructureTemplate>> structures = new HashMap<String, ArrayList<StructureTemplate>>();
        for (String key : root.keySet()) {
            JsonElement jsonElement = root.get(key);
            if (!(jsonElement instanceof JsonArray)) continue;
            JsonArray arr = (JsonArray)jsonElement;
            for (JsonElement jsonElement2 : arr) {
                if (!(jsonElement2 instanceof JsonObject)) continue;
                JsonObject obj = (JsonObject)jsonElement2;
                structures.computeIfAbsent(key, k -> new ArrayList()).add(new StructureTemplate(key, obj.getAsJsonArray("entities").asList().stream().map(JsonElement::getAsString).collect(Collectors.toSet()), obj.get("loadout").getAsString(), obj.get("chance").getAsFloat()));
            }
        }
        return structures;
    }

    public static HashMap<String, Set<EntityType<?>>> loadFactions(Path pathToDef) throws IOException {
        JsonObject root = (JsonObject)GSON.fromJson((Reader)new FileReader(pathToDef.toFile()), JsonObject.class);
        if (root == null) {
            throw new TemplateException("no JsonObject present in tacznpc_factions.json!");
        }
        HashMap<String, Set<EntityType<?>>> factions = new HashMap<>();
        root.asMap().forEach((str, element) -> {
            if (!(element instanceof JsonArray)) {
                throw new TemplateException("non-JsonArray found in factions config!");
            }
            JsonArray arr = (JsonArray)element;
            factions.put((String)str, arr.asList().stream().map(e -> EntityType.byString(e.getAsString()).orElseThrow(() -> new TemplateException("Invalid EntityType in faction %s! id: %s".formatted(str, e)))).collect(Collectors.toSet()));
        });
        return factions;
    }

    public static HashMap<String, NPCTemplate> loadTemplates(MinecraftServer server, Path pathToDef) throws IOException {
        JsonObject root = (JsonObject)GSON.fromJson((Reader)new FileReader(pathToDef.toFile()), JsonObject.class);
        if (root == null) {
            throw new TemplateException("no JsonObject present in tacznpc_loadouts.json!");
        }
        HashMap<String, NPCTemplate> templates = new HashMap<String, NPCTemplate>();
        root.asMap().forEach((str, e) -> {
            List<NPCTemplate.SkinTemplate> texture;
            Either<LootTable, ItemStack[]> secondary;
            Either<LootTable, ItemStack[]> primary;
            Either<LootTable, ItemStack[]> boots;
            Either<LootTable, ItemStack[]> leggings;
            Either<LootTable, ItemStack[]> chestplate;
            Either<LootTable, ItemStack[]> helmet;
            EquipmentTable equipmentTable;
            List<String> names;
            NpcRelations.Definition relations;
            if (e instanceof JsonObject) {
                JsonObject obj = (JsonObject)e;
                relations = NpcRelations.read(str, obj);
                names = obj.has("names") ? obj.getAsJsonArray("names").asList().stream().map(JsonElement::getAsString).toList() : List.of();
                equipmentTable = null;
                if (obj.has("equipment")) {
                    JsonObject eObj = obj.getAsJsonObject("equipment");
                    ResourceKey<LootTable> lootTable = eObj.has("table") ? ResourceKey.create((ResourceKey)Registries.LOOT_TABLE, (ResourceLocation)ResourceLocation.parse((String)eObj.get("table").getAsString())) : BuiltInLootTables.EMPTY;
                    Map<EquipmentSlot, Float> dropChances = eObj.has("dropChances") ? eObj.getAsJsonObject("dropChances").entrySet().stream().collect(Collectors.toMap(key -> EquipmentSlot.byName((String)((String)key.getKey()).toLowerCase(Locale.ROOT)), value -> Float.valueOf(((JsonElement)value.getValue()).getAsFloat()))) : Map.of(EquipmentSlot.MAINHAND, Float.valueOf(0.1f), EquipmentSlot.OFFHAND, Float.valueOf(0.1f), EquipmentSlot.HEAD, Float.valueOf(0.1f), EquipmentSlot.CHEST, Float.valueOf(0.1f), EquipmentSlot.LEGS, Float.valueOf(0.1f), EquipmentSlot.FEET, Float.valueOf(0.1f));
                    equipmentTable = new EquipmentTable(lootTable, dropChances);
                }
                helmet = obj.has("helmet") ? TemplateDataManager.getItemEntry(server, obj.get("helmet").getAsString()) : Either.right(EMPTY_ITEM);
                chestplate = obj.has("chestplate") ? TemplateDataManager.getItemEntry(server, obj.get("chestplate").getAsString()) : Either.right(EMPTY_ITEM);
                leggings = obj.has("leggings") ? TemplateDataManager.getItemEntry(server, obj.get("leggings").getAsString()) : Either.right(EMPTY_ITEM);
                boots = obj.has("boots") ? TemplateDataManager.getItemEntry(server, obj.get("boots").getAsString()) : Either.right(EMPTY_ITEM);
                primary = obj.has("primary") ? TemplateDataManager.getItemEntry(server, obj.get("primary").getAsString()) : Either.right(EMPTY_ITEM);
                Either<LootTable, ItemStack[]> either = secondary = obj.has("secondary") ? TemplateDataManager.getItemEntry(server, obj.get("secondary").getAsString()) : Either.right(EMPTY_ITEM);
                if (obj.has("skin")) {
                    JsonElement patt0$temp = obj.get("skin");
                    if (patt0$temp instanceof JsonArray) {
                        JsonArray texArr = (JsonArray)patt0$temp;
                        texture = new ArrayList<NPCTemplate.SkinTemplate>(texArr.asList().stream().map(em -> {
                            JsonObject o = em.getAsJsonObject();
                            return new NPCTemplate.SkinTemplate(o.get("id").getAsString(), o.has("slim") && o.get("slim").getAsBoolean());
                        }).toList());
                    } else {
                        JsonElement patt1$temp = obj.get("skin");
                        if (patt1$temp instanceof JsonObject) {
                            JsonObject texObj = (JsonObject)patt1$temp;
                            texture = List.of(new NPCTemplate.SkinTemplate(texObj.get("id").getAsString(), texObj.has("slim") && texObj.get("slim").getAsBoolean()));
                        } else {
                            texture = List.of(NPCTemplate.DEFAULT_SKIN);
                        }
                    }
                } else {
                    texture = List.of(NPCTemplate.DEFAULT_SKIN);
                }
            } else {
                throw new TemplateException("non-JsonObject found in loadout config!");
            }
            templates.put((String)str, new NPCTemplate(relations.faction(), relations.values(), names, equipmentTable, helmet, chestplate, leggings, boots, primary, secondary, texture));
        });
        return templates;
    }

    public static Either<LootTable, ItemStack[]> getItemEntry(MinecraftServer server, String e) {
        if (e.startsWith("%")) {
            e = e.substring(1);
            return Either.right(new ItemStack[]{GunItemBuilder.create().setFireMode(FireMode.SEMI).setId(ResourceLocation.parse((String)e)).build((HolderLookup.Provider)server.registryAccess())});
        }
        if (e.startsWith("$")) {
            e = e.substring(1);
            ResourceKey<LootTable> tableKey = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(e));
            LootTable lootTable = server.reloadableRegistries().getLootTable(tableKey);
            return Either.left(lootTable);
        }
        if (e.startsWith("#")) {
            e = e.substring(1);
            ResourceLocation tagLocation = ResourceLocation.parse((String)e);
            TagKey<Item> myItemTagKey = TagKey.create(Registries.ITEM, tagLocation);
            HolderLookup.RegistryLookup<Item> itemLookup = server.registryAccess().lookupOrThrow(Registries.ITEM);
            Optional<HolderSet.Named<Item>> optionalTag = itemLookup.get(myItemTagKey);
            ArrayList<ItemStack> items = new ArrayList<ItemStack>();
            if (optionalTag.isPresent()) {
                HolderSet.Named<Item> tagHolders = optionalTag.get();
                for (Holder<Item> itemHolder : tagHolders) {
                    items.add(((Item)itemHolder.value()).getDefaultInstance());
                }
            }
            return Either.right(((ItemStack[])items.toArray(ItemStack[]::new)));
        }
        Registry<Item> ireg = server.registryAccess().registry(Registries.ITEM).get();
        return Either.right(new ItemStack[]{((Item)ireg.get(ResourceLocation.parse((String)e))).getDefaultInstance()});
    }
}

