package za.co.neroland.neroevents.payload.debris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

import za.co.neroland.neroevents.payload.debris.WreckDecay.CellKind;
import za.co.neroland.neroevents.payload.debris.WreckDecay.Replacement;
import za.co.neroland.neroevents.payload.debris.WreckDecay.WreckRecord;
import za.co.neroland.neroevents.testing.core.CoreFixtures;

/** Wreck decay with a fixed clock: stage timing, unloaded chunks, crates with loot, identity-only replacement. */
class WreckDecayTest {

    private static final long DAY = WreckDecay.DAY_MS;
    private static final int LINGER = 3;
    private static final Identifier VARIANT = Identifier.fromNamespaceAndPath("neroevents", "wreck_pod_shell");

    private static final BlockPos HULL = new BlockPos(0, 64, 0);
    private static final BlockPos STRUT = new BlockPos(1, 64, 0);
    private static final BlockPos CRATE = new BlockPos(2, 64, 0);

    /** An in-memory store. */
    static final class MemoryStore implements WreckDecay.Store {
        List<WreckRecord> records = new ArrayList<>();
        int cursor;

        @Override
        public List<WreckRecord> records() {
            return List.copyOf(records);
        }

        @Override
        public void replaceAll(List<WreckRecord> next) {
            records = new ArrayList<>(next);
        }

        @Override
        public int cursor() {
            return cursor;
        }

        @Override
        public void cursor(int next) {
            cursor = next;
        }
    }

    /** Cells by position; positions not in {@code kinds} are foreign; {@code unloaded} answers null. */
    static final class FakeCells implements WreckDecay.DecayWorld {
        final Map<BlockPos, CellKind> kinds = new HashMap<>();
        final Set<BlockPos> unloaded = new HashSet<>();
        final List<String> replaced = new ArrayList<>();

        @Override
        public CellKind kind(Identifier dimension, BlockPos pos) {
            if (unloaded.contains(pos)) {
                return null;
            }
            return kinds.getOrDefault(pos, CellKind.NOT_OURS);
        }

        @Override
        public void replace(Identifier dimension, BlockPos pos, Replacement replacement) {
            replaced.add(pos.getX() + ":" + replacement);
            kinds.put(pos, switch (replacement) {
                case SCORCHED_PANEL -> CellKind.SCORCHED_PANEL;
                case RUBBLE, AIR -> CellKind.NOT_OURS;
            });
        }
    }

    private static WreckRecord record(long placedAt, List<BlockPos> cells) {
        return new WreckRecord(UUID.randomUUID(), CoreFixtures.DIM, BlockPos.ZERO, VARIANT, Rotation.NONE, placedAt, 0,
                cells);
    }

    private static FakeCells wreck(CellKind crate) {
        FakeCells cells = new FakeCells();
        cells.kinds.put(HULL, CellKind.HULL_PLATING);
        cells.kinds.put(STRUT, CellKind.BENT_STRUT);
        cells.kinds.put(CRATE, crate);
        return cells;
    }

    @Test
    void stagesAdvanceAtLingerAndTwiceLinger() {
        MemoryStore store = new MemoryStore();
        store.records.add(record(0L, List.of(HULL, STRUT, CRATE)));
        FakeCells cells = wreck(CellKind.CRATE_EMPTY);

        assertEquals(0, WreckDecay.pass(store, cells, LINGER * DAY - 1, LINGER));
        assertEquals(0, store.records.get(0).stage());

        assertEquals(1, WreckDecay.pass(store, cells, LINGER * DAY, LINGER));
        assertEquals(1, store.records.get(0).stage());
        assertEquals(CellKind.SCORCHED_PANEL, cells.kind(CoreFixtures.DIM, HULL));
        assertEquals(CellKind.NOT_OURS, cells.kind(CoreFixtures.DIM, STRUT));
        assertEquals(CellKind.NOT_OURS, cells.kind(CoreFixtures.DIM, CRATE));

        assertEquals(0, WreckDecay.pass(store, cells, 2 * LINGER * DAY - 1, LINGER));
        assertEquals(1, WreckDecay.pass(store, cells, 2 * LINGER * DAY, LINGER));
        assertTrue(store.records.isEmpty(), "nothing of ours remains: forgotten");
        assertTrue(cells.replaced.contains("0:RUBBLE"));
    }

    @Test
    void anUnloadedChunkLeavesTheRecordForALaterPass() {
        MemoryStore store = new MemoryStore();
        store.records.add(record(0L, List.of(HULL, STRUT)));
        FakeCells cells = wreck(CellKind.CRATE_EMPTY);
        cells.unloaded.add(STRUT);
        WreckDecay.pass(store, cells, LINGER * DAY, LINGER);
        assertEquals(0, store.records.get(0).stage(), "not every cell was reachable");
        assertEquals(CellKind.SCORCHED_PANEL, cells.kind(CoreFixtures.DIM, HULL), "partial progress is kept");
        cells.unloaded.clear();
        WreckDecay.pass(store, cells, LINGER * DAY, LINGER);
        assertEquals(1, store.records.get(0).stage());
    }

    @Test
    void aCrateStillHoldingLootBlocksStageTwo() {
        MemoryStore store = new MemoryStore();
        store.records.add(record(0L, List.of(HULL, CRATE)));
        FakeCells cells = wreck(CellKind.CRATE_WITH_LOOT);
        WreckDecay.pass(store, cells, LINGER * DAY, LINGER);
        WreckDecay.pass(store, cells, 3 * LINGER * DAY, LINGER);
        assertEquals(1, store.records.size());
        assertEquals(1, store.records.get(0).stage(), "held at stage 1 while the crate has loot");
        assertEquals(CellKind.CRATE_WITH_LOOT, cells.kind(CoreFixtures.DIM, CRATE));
        cells.kinds.put(CRATE, CellKind.CRATE_EMPTY);
        WreckDecay.pass(store, cells, 3 * LINGER * DAY, LINGER);
        assertTrue(store.records.isEmpty());
    }

    @Test
    void zeroLingerDaysNeverDecays() {
        MemoryStore store = new MemoryStore();
        store.records.add(record(0L, List.of(HULL)));
        FakeCells cells = wreck(CellKind.CRATE_EMPTY);
        assertEquals(0, WreckDecay.pass(store, cells, 1000L * DAY, 0));
        assertEquals(CellKind.HULL_PLATING, cells.kind(CoreFixtures.DIM, HULL));
        assertTrue(cells.replaced.isEmpty());
    }

    @Test
    void atMostThirtyTwoRecordsPerPass() {
        MemoryStore store = new MemoryStore();
        FakeCells cells = new FakeCells();
        for (int i = 0; i < 40; i++) {
            BlockPos pos = new BlockPos(i * 10, 64, 0);
            cells.kinds.put(pos, CellKind.HULL_PLATING);
            store.records.add(record(0L, List.of(pos)));
        }
        assertEquals(WreckDecay.MAX_PER_PASS, WreckDecay.pass(store, cells, LINGER * DAY, LINGER));
        long advanced = store.records.stream().filter(r -> r.stage() == 1).count();
        assertEquals(WreckDecay.MAX_PER_PASS, advanced);
        assertEquals(8, WreckDecay.pass(store, cells, LINGER * DAY, LINGER), "the round robin reaches the rest");
        assertEquals(40, store.records.stream().filter(r -> r.stage() == 1).count());
    }

    @Test
    void aForeignBlockAtACellIsUntouched() {
        MemoryStore store = new MemoryStore();
        BlockPos foreign = new BlockPos(9, 64, 9);
        store.records.add(record(0L, List.of(HULL, foreign)));
        FakeCells cells = wreck(CellKind.CRATE_EMPTY);
        WreckDecay.pass(store, cells, LINGER * DAY, LINGER);
        WreckDecay.pass(store, cells, 2 * LINGER * DAY, LINGER);
        for (String r : cells.replaced) {
            assertTrue(r.startsWith("0:"), "only our hull cell was replaced: " + r);
        }
    }
}
