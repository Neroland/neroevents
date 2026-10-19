package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import za.co.neroland.neroevents.event.anchor.AnchorPicker;
import za.co.neroland.neroevents.event.world.DensityCells;
import za.co.neroland.neroevents.event.world.WorldView;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.RecordingPayloadType;
import za.co.neroland.neroevents.testing.TestSources.DenyAllSource;
import za.co.neroland.neroevents.testing.TestSources.DenyInBoxSource;

/** The density-cell picker: protection-aware, deterministic, position-free, never loads a chunk. */
class AnchorPickerTest {

    private static final int CELL = 256;

    private static AnchorPicker.Result pick(FakeWorldView world, ProtectionQuery protection, long seed) {
        return AnchorPicker.pick(world, protection, FakeWorldView.OVERWORLD,
                a -> Footprint.point(FakeWorldView.OVERWORLD, a), seed, CELL, 16, 4, 0);
    }

    /** Two dense cells far apart (no shared neighbours), all chunks loaded. */
    private static FakeWorldView twoCells() {
        return new FakeWorldView().allChunksLoaded(FakeWorldView.OVERWORLD)
                .denseCell(FakeWorldView.OVERWORLD, 0, 0, 5)
                .denseCell(FakeWorldView.OVERWORLD, 10, 10, 5);
    }

    @Test
    void anchorAvoidsADeniedCell() {
        FakeWorldView world = twoCells();
        // Deny cell (0,0) and its neighbours: everything within [-256, 511] on both axes.
        ProtectionQuery query = new ProtectionQuery(List.of(new DenyInBoxSource("box", -256, -256, 511, 511)));
        for (long seed = 0; seed < 20; seed++) {
            // Enough cell rolls to visit every candidate cell, so the denied cluster cannot exhaust them.
            AnchorPicker.Result result = AnchorPicker.pick(world, query, FakeWorldView.OVERWORLD,
                    a -> Footprint.point(FakeWorldView.OVERWORLD, a), seed, CELL, 16, 18, 0);
            AnchorPicker.Found found = assertInstanceOf(AnchorPicker.Found.class, result, "seed " + seed);
            assertTrue(found.anchor().getX() >= 9 * CELL && found.anchor().getZ() >= 9 * CELL, "seed " + seed);
        }
    }

    @Test
    void denyAllYieldsEmptyWithTheSourceAndASkippedInstance() {
        AnchorPicker.Result result = pick(twoCells(), new ProtectionQuery(List.of(new DenyAllSource("all"))), 3L);
        AnchorPicker.Empty empty = assertInstanceOf(AnchorPicker.Empty.class, result);
        assertEquals("no_anchor", empty.reason());
        assertEquals("all", empty.deniedBy());

        try (TestEngine t = new TestEngine(List.of(new DenyAllSource("all")))) {
            t.settings.schedulerEnabled = true;
            EventDefinition def = t.define("denied", new RecordingPayloadType("denied"), null);
            t.clock.now = 600L;
            t.engine.tickOnce();
            assertTrue(t.engine.live().isEmpty());
            EventInstance skipped = t.engine.recent().get(0);
            assertEquals(EventPhase.SKIPPED, skipped.phase());
            assertEquals("no_anchor", skipped.reason());
            assertEquals(600L + def.cooldownTicks() / 2, t.state.cooldownUntil(def.id()));
            RequestResult refused = t.engine.request(new EventRequest(def.id(), FakeWorldView.OVERWORLD,
                    EventOrigin.ADMIN, null));
            assertEquals("no_anchor", ((RequestResult.Refused) refused).code());
            RequestResult explicit = t.engine.request(new EventRequest(def.id(), FakeWorldView.OVERWORLD,
                    EventOrigin.ADMIN, new BlockPos(5, 64, 5)));
            assertEquals("protected", ((RequestResult.Refused) explicit).code());
            assertEquals("all", ((RequestResult.Refused) explicit).message());
        }
    }

    @Test
    void sameSeedSameAnchor() {
        ProtectionQuery open = new ProtectionQuery(List.of());
        AnchorPicker.Found a = (AnchorPicker.Found) pick(twoCells(), open, 99L);
        AnchorPicker.Found b = (AnchorPicker.Found) pick(twoCells(), open, 99L);
        assertEquals(a.anchor(), b.anchor());
        assertEquals(a.cell(), b.cell());
    }

    @Test
    void unloadedChunksAreSkippedWithoutLoading() {
        // Only one chunk of cell (0,0) is loaded; the fake throws if surfaceY is asked for any other.
        FakeWorldView world = new FakeWorldView().denseCell(FakeWorldView.OVERWORLD, 0, 0, 3)
                .loadedChunks(FakeWorldView.OVERWORLD, 0, 0, 0, 0);
        int founds = 0;
        for (long seed = 0; seed < 64; seed++) {
            AnchorPicker.Result result = AnchorPicker.pick(world, new ProtectionQuery(List.of()),
                    FakeWorldView.OVERWORLD, a -> Footprint.point(FakeWorldView.OVERWORLD, a), seed, CELL, 64, 16, 0);
            if (result instanceof AnchorPicker.Found found) {
                founds++;
                assertEquals(0, found.anchor().getX() >> 4, "seed " + seed);
                assertEquals(0, found.anchor().getZ() >> 4, "seed " + seed);
            } else {
                assertInstanceOf(AnchorPicker.Empty.class, result, "seed " + seed);
            }
        }
        assertTrue(founds > 0, "some seed must find the one loaded chunk");
        assertTrue(world.isChunkLoadedCalls > 0);
        assertTrue(world.surfaceYCalls <= world.isChunkLoadedCalls);
    }

    @Test
    void theWorldViewHasNoPlayerPositionAccessor() {
        for (Method method : WorldView.class.getMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(method.getReturnType() == UUID.class, method.getName());
            if (name.contains("player")) {
                Class<?> type = method.getReturnType();
                assertTrue(type == int.class || type == Map.class, method.getName() + " must return a count");
            }
        }
        // The picker reads density counts and nothing about players.
        FakeWorldView world = twoCells();
        pick(world, new ProtectionQuery(List.of()), 1L);
        assertEquals(1, world.densityCalls);
    }

    @Test
    void anchorsKeepTheStandOffFromPlayers() {
        // Small cells so the stand-off circle covers a good share of the candidate area.
        int cell = 32;
        FakeWorldView world = new FakeWorldView().allChunksLoaded(FakeWorldView.OVERWORLD)
                .playerAt(FakeWorldView.OVERWORLD, 100, 100);
        ProtectionQuery open = new ProtectionQuery(List.of());
        boolean insideWithoutStandOff = false;
        for (long n = 0; n < 50; n++) {
            // Spread seeds: consecutive small seeds give java.util.Random near-identical first draws.
            long seed = n * 0x9E3779B97F4A7C15L;
            AnchorPicker.Result result = AnchorPicker.pick(world, open, FakeWorldView.OVERWORLD,
                    a -> Footprint.point(FakeWorldView.OVERWORLD, a), seed, cell, 16, 4, 24);
            AnchorPicker.Found found = assertInstanceOf(AnchorPicker.Found.class, result, "seed " + seed);
            int distance = Math.max(Math.abs(found.anchor().getX() - 100), Math.abs(found.anchor().getZ() - 100));
            assertTrue(distance > 24, "seed " + seed + " landed " + distance + " blocks from the player");

            AnchorPicker.Found off = (AnchorPicker.Found) AnchorPicker.pick(world, open, FakeWorldView.OVERWORLD,
                    a -> Footprint.point(FakeWorldView.OVERWORLD, a), seed, cell, 16, 4, 0);
            if (Math.max(Math.abs(off.anchor().getX() - 100), Math.abs(off.anchor().getZ() - 100)) <= 24) {
                insideWithoutStandOff = true;
            }
        }
        assertTrue(insideWithoutStandOff, "with the stand-off off, some seed lands inside the circle");
        assertEquals(1, world.playersWithinBlocks(FakeWorldView.OVERWORLD, 110, 90, 24));
        assertEquals(0, world.playersWithinBlocks(FakeWorldView.OVERWORLD, 125, 100, 24));
    }

    @Test
    void emptyDimensionFallsBackToTheSpawnCell() {
        FakeWorldView world = new FakeWorldView().allChunksLoaded(FakeWorldView.OVERWORLD)
                .spawn(FakeWorldView.OVERWORLD, new BlockPos(1000, 64, 1000));
        DensityCells cells = DensityCells.of(world, FakeWorldView.OVERWORLD, CELL);
        assertEquals(9, cells.weights().size());
        assertEquals(1, cells.weight(DensityCells.cellOf(1000, 1000, CELL)));
        AnchorPicker.Found found = (AnchorPicker.Found) pick(world, new ProtectionQuery(List.of()), 2L);
        assertTrue(Math.abs(found.anchor().getX() - 1000) < 2 * CELL);
    }
}
