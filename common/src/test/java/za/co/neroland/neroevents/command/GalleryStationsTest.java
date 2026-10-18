package za.co.neroland.neroevents.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.command.GalleryLayout.Box;
import za.co.neroland.neroevents.command.GalleryStations.DecayCell;
import za.co.neroland.neroevents.command.GalleryStations.DecayKind;
import za.co.neroland.neroevents.command.GalleryStations.Station;
import za.co.neroland.neroevents.command.GalleryStations.YardWreck;
import za.co.neroland.neroevents.payload.debris.WreckLayout;
import za.co.neroland.neroevents.payload.debris.WreckLayouts;

/** Every shipped event type has a station, each station says which sibling it needs, the wreck yard fits. */
class GalleryStationsTest {

    private static final Identifier DEBUG = Identifier.fromNamespaceAndPath("neroevents", "debug_marker");

    /** Payload type → the union of its definitions' {@code conditions.requires_mods}. */
    private static Map<Identifier, Set<String>> shippedTypes() throws IOException {
        Path events = repoRoot().resolve("common/src/main/resources/data/neroevents/neroevents/events");
        Map<Identifier, Set<String>> out = new HashMap<>();
        try (Stream<Path> files = Files.walk(events)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                Identifier type = Identifier.parse(json.get("type").getAsString());
                Set<String> mods = out.computeIfAbsent(type, t -> new TreeSet<>());
                JsonObject conditions = json.getAsJsonObject("conditions");
                if (conditions != null && conditions.has("requires_mods")) {
                    JsonArray requires = conditions.getAsJsonArray("requires_mods");
                    for (JsonElement mod : requires) {
                        mods.add(mod.getAsString());
                    }
                }
            }
        }
        out.remove(DEBUG);
        return out;
    }

    @Test
    void everyShippedPayloadTypeHasAStationThatNamesItsSibling() throws IOException {
        Map<Identifier, Set<String>> types = shippedTypes();
        assertTrue(types.size() >= 10, "the scan found the shipped definitions: " + types.keySet());
        Map<String, Set<String>> needsByStation = new HashMap<>();
        for (Map.Entry<Identifier, Set<String>> entry : types.entrySet()) {
            Optional<Station> station = GalleryStations.forPayload(entry.getKey());
            assertTrue(station.isPresent(), "no station for " + entry.getKey());
            needsByStation.computeIfAbsent(station.get().id(), s -> new TreeSet<>()).addAll(entry.getValue());
        }
        for (Station station : GalleryStations.ALL) {
            Set<String> needs = needsByStation.get(station.id());
            if (needs == null) {
                continue; // the wreck yard: the debris type is owned by the debris station
            }
            assertTrue(needs.size() <= 1, station.id() + " needs " + needs);
            String expected = needs.isEmpty() ? null : needs.iterator().next();
            assertEquals(expected, station.needsMod(), station.id());
        }
        assertEquals(null, GalleryStations.WRECK_YARD.needsMod());
    }

    @Test
    void cueIdsAreDistinctAndCoverEveryLoopStation() {
        assertEquals(GalleryStations.CUE_IDS.size(), new HashSet<>(GalleryStations.CUE_IDS).size());
        for (String loop : List.of("supply_drop", "meteor_shower", "debris_crash", "faction_war")) {
            assertTrue(GalleryStations.byId(loop).isPresent(), loop);
            assertTrue(GalleryStations.CUE_IDS.contains(loop), loop);
        }
        assertTrue(GalleryStations.CUE_IDS.contains("console"));
        assertEquals(List.of("solar", "dust", "ion", "cryo", "off"), GalleryStations.STORM_IDS);
    }

    private static WreckLayout shipped(String name) {
        String path = "data/neroevents/neroevents/wrecks/" + name + ".json";
        try (InputStream in = GalleryStationsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return WreckLayouts.decode(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .orElseThrow();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void decayStagesMirrorTheRealDecayForEmptyCrates() {
        WreckLayout shell = shipped("wreck_pod_shell");
        Map<String, DecayKind> one = new HashMap<>();
        GalleryStations.decay(shell, 1).forEach(c -> one.put(c.x() + "," + c.y() + "," + c.z(), c.kind()));
        Map<String, DecayKind> two = new HashMap<>();
        GalleryStations.decay(shell, 2).forEach(c -> two.put(c.x() + "," + c.y() + "," + c.z(), c.kind()));
        assertTrue(GalleryStations.decay(shell, 0).isEmpty());
        for (WreckLayout.Placement p : shell.placements()) {
            String key = p.x() + "," + p.y() + "," + p.z();
            switch (p.kind()) {
                case HULL_PLATING -> {
                    assertEquals(DecayKind.SCORCHED_PANEL, one.get(key), key);
                    assertEquals(DecayKind.RUBBLE, two.get(key), key);
                }
                case SCORCHED_PANEL -> {
                    assertFalse(one.containsKey(key), key);
                    assertEquals(DecayKind.RUBBLE, two.get(key), key);
                }
                case BENT_STRUT, SALVAGE_CRATE -> {
                    assertEquals(DecayKind.AIR, one.get(key), key);
                    assertEquals(DecayKind.AIR, two.get(key), key);
                }
            }
        }
        for (DecayCell cell : GalleryStations.decay(shell, 2)) {
            assertNotNull(cell.kind());
        }
    }

    private static Box boxOf(YardWreck wreck, WreckLayout layout) {
        int minY = 0;
        for (WreckLayout.Placement p : layout.placements()) {
            minY = Math.min(minY, p.y());
        }
        return new Box(wreck.origin().dx(), wreck.origin().dy() + minY, wreck.origin().dz(),
                wreck.origin().dx() + layout.sizeX() - 1, wreck.origin().dy() + layout.sizeY() - 1,
                wreck.origin().dz() + layout.sizeZ() - 1);
    }

    @Test
    void theWreckYardPlacesEveryLayoutInsideItsBayWithoutOverlap() {
        List<Box> boxes = new ArrayList<>();
        Set<String> variants = new HashSet<>();
        Box yard = GalleryStations.WRECK_YARD.bay();
        Box yardWithFloor = new Box(yard.minDx(), 0, yard.minDz(), yard.maxDx(), yard.maxDy(), yard.maxDz());
        for (YardWreck wreck : GalleryStations.YARD) {
            WreckLayout layout = shipped(wreck.variant());
            Box box = boxOf(wreck, layout);
            assertTrue(yardWithFloor.contains(box), wreck.variant() + " " + box);
            assertTrue(box.contains(wreck.centre()), wreck.variant() + " centre inside its box");
            for (Box other : boxes) {
                assertFalse(other.overlaps(box), wreck.variant() + " overlaps " + other);
            }
            boxes.add(box);
            variants.add(wreck.variant());
            assertFalse(wreck.label().isBlank());
        }
        assertEquals(Set.of("wreck_pod_shell", "wreck_strut_field", "wreck_cargo_spill", "wreck_hull_rib"), variants);
        assertEquals(Set.of(0, 1, 2), new HashSet<>(GalleryStations.YARD.stream().map(YardWreck::stage).toList()));
        // The debris station's wreck sits in its bay, and the crash lands on its crate.
        YardWreck debris = GalleryStations.DEBRIS_WRECK;
        WreckLayout shell = shipped(debris.variant());
        Box box = boxOf(debris, shell);
        assertTrue(GalleryStations.DEBRIS_CRASH.bay().contains(box), box.toString());
        boolean crateUnderImpact = shell.placements().stream().anyMatch(p -> p.kind() == WreckLayout.Kind.SALVAGE_CRATE
                && debris.origin().dx() + p.x() == GalleryStations.DEBRIS_IMPACT.dx()
                && debris.origin().dz() + p.z() == GalleryStations.DEBRIS_IMPACT.dz()
                && debris.origin().dy() + p.y() + 1 == GalleryStations.DEBRIS_IMPACT.dy());
        assertTrue(crateUnderImpact, "the debris lands on the crate's top face");
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("common").resolve("src").resolve("main"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found above " + Path.of("").toAbsolutePath());
    }
}
