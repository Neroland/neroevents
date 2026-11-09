package za.co.neroland.neroevents.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Recipe and obtainability guards. Reads the source resource folders directly (shared and every
 * {@code resources-<mc>} overlay), so every node checks the same files whichever set its classpath carries.
 */
class RecipeDataTest {
    /** Event-only things: a recipe would bypass the event (or quest) that places or awards them. */
    private static final Set<String> NEVER_CRAFTED = Set.of(
            "neroevents:war_beacon", "neroevents:salvage_crate", "neroevents:crater_pocket",
            "neroevents:embedded_meteor_ore", "neroevents:supply_beacon");
    /** Event-placed blocks that must not drop themselves when broken. */
    private static final Set<String> NO_SELF_DROP = Set.of("war_beacon", "salvage_crate", "crater_pocket");
    /** The salvage items: each needs at least one use (a recipe ingredient or a commodity entry). */
    private static final Set<String> SALVAGE = Set.of(
            "neroevents:salvage_plating", "neroevents:salvage_conduit", "neroevents:salvage_core_fragment");
    /** Neroland Core items a recipe may name; all exist in every Core this mod accepts. */
    private static final Set<String> CORE_ITEMS = Set.of(
            "nerolandcore:starsteel_nugget", "nerolandcore:starsteel_ingot", "nerolandcore:starsteel_dust",
            "nerolandcore:nero_alloy_nugget", "nerolandcore:nero_alloy_ingot", "nerolandcore:nero_alloy_dust");
    /** Neroland Core tags whose own entries are Core items, so they are never empty on a Core-only server. */
    private static final Set<String> CORE_TAGS = Set.of("#neroland:meteor/grindable");
    /** Item model files that exist for something NeroEvents does not register. */
    private static final Set<String> UNREGISTERED_ASSETS = Set.of("reward_crate_token");
    private static final Set<String> BOOK_TYPES = Set.of(
            "minecraft:crafting_shaped", "minecraft:crafting_shapeless", "minecraft:smelting",
            "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:stonecutting");

    @Test
    void shippedRecipesAreTheAuditedSet() throws IOException {
        assertEquals(Set.of("event_console", "starsteel_nugget_from_smelting_salvage_plating",
                "starsteel_nugget_from_blasting_salvage_plating"), recipes().keySet(),
                "a recipe was added or removed: update this list and the wiki's Getting-the-items notes");
    }

    @Test
    void everyRecipeNamesOnlyKnownItems() throws IOException {
        List<String> problems = new ArrayList<>();
        for (var entry : recipes().entrySet()) {
            JsonObject recipe = entry.getValue();
            String type = recipe.get("type").getAsString();
            if (!BOOK_TYPES.contains(type)) {
                problems.add(entry.getKey() + ": unexpected type " + type);
            }
            for (String id : ingredientIds(recipe)) {
                if (id.startsWith("#")) {
                    // A tag that can be empty on a Core-only server would make the recipe uncraftable.
                    if (!id.startsWith("#minecraft:") && !CORE_TAGS.contains(id)) {
                        problems.add(entry.getKey() + ": ingredient tag may be empty without siblings: " + id);
                    }
                } else if (!known(id)) {
                    problems.add(entry.getKey() + ": unknown ingredient " + id);
                }
            }
            String result = resultId(recipe);
            if (!known(result)) {
                problems.add(entry.getKey() + ": unknown result " + result);
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void eventOnlyThingsHaveNoRecipe() throws IOException {
        for (var entry : recipes().entrySet()) {
            assertFalse(NEVER_CRAFTED.contains(resultId(entry.getValue())),
                    entry.getKey() + " makes an event-only item craftable");
        }
        for (String id : NEVER_CRAFTED) {
            assertTrue(known(id), "not a NeroEvents item: " + id);
        }
    }

    @Test
    void eventPlacedBlocksDoNotDropThemselves() throws IOException {
        for (Path base : resourceSets()) {
            for (String block : NO_SELF_DROP) {
                assertFalse(Files.exists(base.resolve("data/neroevents/loot_table/blocks/" + block + ".json")),
                        base + ": " + block + " must not have a block loot table");
            }
        }
    }

    @Test
    void everyRecipeUnlocksInTheRecipeBook() throws IOException {
        Path data = shared().resolve("data/neroevents");
        for (var entry : recipes().entrySet()) {
            String category = entry.getValue().has("category") ? entry.getValue().get("category").getAsString() : "misc";
            Path unlock = data.resolve("advancement/recipes/" + category + "/" + entry.getKey() + ".json");
            assertTrue(Files.exists(unlock), "run tools/gen_recipe_unlocks.py: missing " + unlock);
            JsonObject advancement = read(unlock);
            assertEquals("neroevents:" + entry.getKey(),
                    advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
        }
    }

    @Test
    void everySalvageItemHasAUse() throws IOException {
        Set<String> used = new TreeSet<>();
        for (JsonObject recipe : recipes().values()) {
            used.addAll(ingredientIds(recipe));
        }
        Set<String> traded = new TreeSet<>();
        JsonObject commodities = read(shared().resolve("data/neroevents/neroeconomy/commodities/neroevents.json"));
        for (JsonElement element : commodities.getAsJsonArray("commodities")) {
            String id = element.getAsJsonObject().get("item").getAsString();
            assertTrue(known(id), "commodity names an unknown item: " + id);
            traded.add(id);
        }
        for (String id : SALVAGE) {
            assertTrue(used.contains(id) || traded.contains(id), id + " is obtainable but nothing uses it");
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static boolean known(String id) {
        if (id.startsWith("minecraft:")) {
            return true;
        }
        if (id.startsWith("nerolandcore:")) {
            return CORE_ITEMS.contains(id);
        }
        if (id.startsWith("neroevents:")) {
            String path = id.substring("neroevents:".length());
            return !UNREGISTERED_ASSETS.contains(path)
                    && Files.exists(shared().resolve("assets/neroevents/items/" + path + ".json"));
        }
        return false;
    }

    private static String resultId(JsonObject recipe) {
        JsonElement result = recipe.get("result");
        if (result.isJsonPrimitive()) {
            return result.getAsString();
        }
        JsonObject object = result.getAsJsonObject();
        return (object.has("id") ? object.get("id") : object.get("item")).getAsString();
    }

    private static List<String> ingredientIds(JsonObject recipe) {
        List<String> out = new ArrayList<>();
        if (recipe.has("key")) {
            for (var value : recipe.getAsJsonObject("key").entrySet()) {
                flatten(value.getValue(), out);
            }
        }
        for (String field : List.of("ingredients", "ingredient", "base", "addition", "template")) {
            if (recipe.has(field)) {
                flatten(recipe.get(field), out);
            }
        }
        return out;
    }

    private static void flatten(JsonElement element, List<String> out) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(e -> flatten(e, out));
        } else if (element.isJsonPrimitive()) {
            out.add(element.getAsString());
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("item")) {
                out.add(object.get("item").getAsString());
            } else if (object.has("tag")) {
                out.add("#" + object.get("tag").getAsString());
            }
        }
    }

    /** Every NeroEvents recipe in the shared resources and every overlay, by name; an overlay copy must parse too. */
    private static Map<String, JsonObject> recipes() throws IOException {
        Map<String, JsonObject> out = new TreeMap<>();
        for (Path base : resourceSets()) {
            Path dir = base.resolve("data/neroevents/recipe");
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    String name = dir.relativize(file).toString().replace('\\', '/').replaceAll("\\.json$", "");
                    out.put(name, read(file));
                }
            }
        }
        return out;
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<Path> resourceSets() throws IOException {
        List<Path> out = new ArrayList<>();
        out.add(shared());
        try (Stream<Path> dirs = Files.list(shared().getParent())) {
            dirs.filter(p -> p.getFileName().toString().startsWith("resources-")).sorted().forEach(out::add);
        }
        return out;
    }

    private static Path shared() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("common/src/main/resources");
            if (Files.isDirectory(candidate.resolve("data/neroevents"))) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("common/src/main/resources not found above " + Path.of("").toAbsolutePath());
    }
}
