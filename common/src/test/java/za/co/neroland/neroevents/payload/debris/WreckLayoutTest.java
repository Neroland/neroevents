package za.co.neroland.neroevents.payload.debris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/** The four shipped wreck layouts decode; crate counts match; rotation remaps facings; bad files are rejected. */
class WreckLayoutTest {

    static WreckLayout shipped(String name) {
        String path = "data/neroevents/neroevents/wrecks/" + name + ".json";
        try (InputStream in = WreckLayoutTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            Optional<WreckLayout> layout = WreckLayouts.decode(json);
            assertTrue(layout.isPresent(), name + " decodes");
            return layout.get();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static Optional<WreckLayout> decode(String json) {
        return WreckLayouts.decode(JsonParser.parseString(json));
    }

    @Test
    void theFourShippedLayoutsDecodeAndCountTheirCrates() {
        for (String name : DebrisCrashPayload.ALL_VARIANTS) {
            WreckLayout layout = shipped(name);
            assertEquals(layout.lootBlocks(), layout.crateCells(), name);
            assertTrue(layout.sizeX() <= 7 && layout.sizeY() <= 5 && layout.sizeZ() <= 7, name + " fits 7x5x7");
        }
        assertEquals(2, shipped("wreck_cargo_spill").lootBlocks());
        assertEquals(1, shipped("wreck_hull_rib").lootBlocks());
    }

    @Test
    void rotationMapsFacingsClockwise() {
        WreckLayout one = decode("{\"size\": [3, 1, 3], \"loot_blocks\": 0, \"layers\": [{\"y\": 0, \"rows\": "
                + "[\"n..\", \"...\", \"...\"]}]}").orElseThrow();
        assertEquals(Direction.NORTH, one.placements().get(0).facing());
        assertEquals(Direction.EAST, one.rotate(Rotation.CLOCKWISE_90).placements().get(0).facing());
        assertEquals(Direction.SOUTH, one.rotate(Rotation.CLOCKWISE_180).placements().get(0).facing());
        assertEquals(Direction.WEST, one.rotate(Rotation.COUNTERCLOCKWISE_90).placements().get(0).facing());
        // The north-west corner turns to the north-east corner.
        WreckLayout.Placement turned = one.rotate(Rotation.CLOCKWISE_90).placements().get(0);
        assertEquals(2, turned.x());
        assertEquals(0, turned.z());
    }

    @Test
    void rotationSwapsTheFootprintAndKeepsEveryCell() {
        WreckLayout field = shipped("wreck_strut_field");
        WreckLayout layout = decode("{\"size\": [4, 1, 2], \"loot_blocks\": 1, \"layers\": [{\"y\": 0, \"rows\": "
                + "[\"P..C\", \"S..e\"]}]}").orElseThrow();
        WreckLayout turned = layout.rotate(Rotation.CLOCKWISE_90);
        assertEquals(2, turned.sizeX());
        assertEquals(4, turned.sizeZ());
        assertEquals(layout.placements().size(), turned.placements().size());
        assertEquals(field.placements().size(), field.rotate(Rotation.CLOCKWISE_180).placements().size());
        List<WreckLayout.Placement> strut = field.rotate(Rotation.CLOCKWISE_90).placements().stream()
                .filter(p -> p.kind() == WreckLayout.Kind.BENT_STRUT).toList();
        assertEquals(5, strut.size());
    }

    @Test
    void aRowOfTheWrongLengthIsRejected() {
        assertTrue(decode("{\"size\": [3, 1, 2], \"loot_blocks\": 0, \"layers\": [{\"y\": 0, \"rows\": "
                + "[\"PPP\", \"PP\"]}]}").isEmpty());
    }

    @Test
    void otherMalformedFilesAreRejected() {
        // wrong row count, y out of range, unknown cell, loot_blocks mismatch
        assertTrue(decode("{\"size\": [2, 1, 2], \"loot_blocks\": 0, \"layers\": [{\"y\": 0, \"rows\": [\"PP\"]}]}")
                .isEmpty());
        assertTrue(decode("{\"size\": [2, 1, 1], \"loot_blocks\": 0, \"layers\": [{\"y\": 3, \"rows\": [\"PP\"]}]}")
                .isEmpty());
        assertTrue(decode("{\"size\": [2, 1, 1], \"loot_blocks\": 0, \"layers\": [{\"y\": 0, \"rows\": [\"PX\"]}]}")
                .isEmpty());
        assertTrue(decode("{\"size\": [2, 1, 1], \"loot_blocks\": 2, \"layers\": [{\"y\": 0, \"rows\": [\"PC\"]}]}")
                .isEmpty());
    }
}
