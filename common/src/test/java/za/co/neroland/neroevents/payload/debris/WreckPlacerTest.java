package za.co.neroland.neroevents.payload.debris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.event.world.WorldView;
import za.co.neroland.neroevents.payload.debris.WreckDecay.WreckRecord;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.protect.ProtectionSource;
import za.co.neroland.neroevents.testing.FakeClock;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.TestSources.DenyInBoxSource;
import za.co.neroland.neroevents.testing.core.CoreFixtures;

/** Wreck placement: structure void, the y −1 ground rule, protection per cell, zero crates ⇒ FAILURE, drift. */
class WreckPlacerTest {

    private static final int GROUND = 63;
    private static final BlockPos ANCHOR = new BlockPos(500, GROUND + 1, 500);

    /** Air above the ground plane, solid ground at and below it; {@code hardGround} cells are unbreakable. */
    static final class FakeWreckWorld implements WreckPlacer.WreckWorld {
        final List<BlockPos> placed = new ArrayList<>();
        final Set<BlockPos> occupied = new HashSet<>();
        boolean groundReplaceable = true;

        @Override
        public boolean isAir(BlockPos pos) {
            return pos.getY() > GROUND && !occupied.contains(pos);
        }

        @Override
        public boolean groundReplaceable(BlockPos pos) {
            return pos.getY() <= GROUND && groundReplaceable;
        }

        @Override
        public void place(BlockPos pos, WreckLayout.Placement placement) {
            placed.add(pos);
            occupied.add(pos);
        }
    }

    private final FakeWorldView view = new FakeWorldView().allChunksLoaded(CoreFixtures.DIM);

    private ProtectionEvaluation evaluation(List<ProtectionSource> sources) {
        return new ProtectionQuery(sources).evaluation(view);
    }

    @BeforeEach
    void setUp() {
        DebrisCrashPayload.resetForTests();
    }

    @AfterEach
    void tearDown() {
        DebrisCrashPayload.resetForTests();
    }

    @Test
    void structureVoidIsNeverPlaced() {
        for (String name : DebrisCrashPayload.ALL_VARIANTS) {
            WreckLayout layout = WreckLayoutTest.shipped(name);
            FakeWreckWorld world = new FakeWreckWorld();
            BlockPos origin = new BlockPos(0, GROUND + 1, 0);
            WreckPlacer.Result result = WreckPlacer.place(world, layout, origin, evaluation(List.of()), CoreFixtures.DIM);
            assertEquals(layout.placements().size(), result.cells().size(), name + ": every non-void cell, nothing else");
            assertEquals(layout.lootBlocks(), result.crates().size(), name);
            for (WreckLayout.Placement p : layout.placements()) {
                assertTrue(world.placed.contains(origin.offset(p.x(), p.y(), p.z())));
            }
        }
    }

    @Test
    void yMinusOneReplacesOnlyReplaceableGround() {
        WreckLayout field = WreckLayoutTest.shipped("wreck_strut_field");
        FakeWreckWorld hard = new FakeWreckWorld();
        hard.groundReplaceable = false;
        WreckPlacer.Result result = WreckPlacer.place(hard, field, new BlockPos(0, GROUND + 1, 0),
                evaluation(List.of()), CoreFixtures.DIM);
        assertTrue(result.crates().isEmpty(), "the half-buried crate needs breakable ground");
        assertEquals(field.placements().size() - 1, result.cells().size());

        FakeWreckWorld soft = new FakeWreckWorld();
        assertEquals(1, WreckPlacer.place(soft, field, new BlockPos(0, GROUND + 1, 0), evaluation(List.of()),
                CoreFixtures.DIM).crates().size());
    }

    @Test
    void anOccupiedCellIsSkipped() {
        WreckLayout layout = WreckLayoutTest.shipped("wreck_hull_rib");
        FakeWreckWorld world = new FakeWreckWorld();
        BlockPos origin = new BlockPos(0, GROUND + 1, 0);
        WreckLayout.Placement first = layout.placements().get(0);
        world.occupied.add(origin.offset(first.x(), first.y(), first.z())); // a tree trunk
        assertEquals(layout.placements().size() - 1,
                WreckPlacer.place(world, layout, origin, evaluation(List.of()), CoreFixtures.DIM).cells().size());
    }

    @Test
    void deniedCellsAreSkipped() {
        WreckLayout layout = WreckLayoutTest.shipped("wreck_cargo_spill");
        FakeWreckWorld world = new FakeWreckWorld();
        BlockPos origin = new BlockPos(0, GROUND + 1, 0);
        // Deny the westmost column (x = 0) of the layout.
        WreckPlacer.Result result = WreckPlacer.place(world, layout, origin,
                evaluation(List.of(new DenyInBoxSource("claim", 0, -10, 0, 10))), CoreFixtures.DIM);
        long column = layout.placements().stream().filter(p -> p.x() == 0).count();
        assertEquals(layout.placements().size() - column, result.cells().size());
        for (BlockPos pos : world.placed) {
            assertFalse(pos.getX() == 0, "nothing placed in the denied column");
        }
    }

    @Test
    void landingDriftBeyondTwoBlocksUsesTheAnchor() {
        WreckLayout layout = WreckLayoutTest.shipped("wreck_pod_shell");
        Footprint pre = DebrisCrashPayload.box(CoreFixtures.DIM, ANCHOR, layout.sizeX(), layout.sizeY(), layout.sizeZ());
        BlockPos near = ANCHOR.east(1);
        BlockPos far = ANCHOR.east(10);
        assertEquals(new BlockPos(near.getX() - 3, GROUND + 6, near.getZ() - 3),
                WreckPlacer.origin(near, GROUND + 5, ANCHOR, GROUND, layout, pre));
        assertEquals(new BlockPos(ANCHOR.getX() - 3, GROUND + 1, ANCHOR.getZ() - 3),
                WreckPlacer.origin(far, GROUND + 5, ANCHOR, GROUND, layout, pre));
    }

    // --- zero crates ⇒ FAILURE (through the payload) ---------------------------------------------------

    /** A debris world over {@link FakeWreckWorld} with the shipped layouts. */
    static final class FakeDebris implements DebrisCrashPayload.DebrisWorld {
        final FakeWreckWorld wreck = new FakeWreckWorld();
        final FakeWorldView view;
        final List<WreckRecord> records = new ArrayList<>();
        final Set<BlockPos> opened = new HashSet<>();
        BlockPos debris;

        FakeDebris(FakeWorldView view) {
            this.view = view;
        }

        @Override
        public WreckLayout layout(Identifier variant) {
            return WreckLayoutTest.shipped(variant.getPath());
        }

        @Override
        public UUID spawnDebris(UUID instance, double x, double y, double z, double vx, double vy, double vz,
                int fallTicks) {
            debris = BlockPos.containing(x, y, z);
            return new UUID(9L, 9L);
        }

        @Override
        public BlockPos debrisPos(UUID uuid) {
            return debris;
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            return true;
        }

        @Override
        public void discard(UUID uuid) {
            debris = null;
        }

        @Override
        public OptionalInt groundY(int x, int z) {
            return OptionalInt.of(GROUND);
        }

        @Override
        public WreckPlacer.WreckWorld wreckWorld(DebrisCrashPayload.State state, DebrisCrashPayload.Config config) {
            return wreck;
        }

        @Override
        public WorldView view() {
            return view;
        }

        @Override
        public boolean crateOpened(BlockPos pos) {
            return opened.contains(pos);
        }

        @Override
        public void recordWreck(WreckRecord record, int max) {
            records.add(record);
        }

        @Override
        public void landingFeedback(BlockPos pos) {
        }
    }

    private record Running(DebrisCrashPayload type, DebrisCrashPayload.State state, FakeDebris world,
            za.co.neroland.neroevents.event.EnginePayloadContext ctx, FakeClock clock) {
    }

    private Running start(String variant, List<ProtectionSource> placementSources) {
        FakeDebris world = new FakeDebris(view);
        DebrisCrashPayload type = new DebrisCrashPayload((server, dim) -> world, EngineSettings::defaults,
                new ProtectionQuery(placementSources), () -> 1_000_000L);
        DebrisCrashPayload.Config config = DebrisCrashPayload.Config.DEFAULT;
        EventDefinition def = CoreFixtures.definition(type, config, 12000, 64);
        EventInstance instance = CoreFixtures.instance(def, ANCHOR, 11L);
        FakeClock clock = new FakeClock(100L);
        var ctx = CoreFixtures.context(instance, view, clock);
        DebrisCrashPayload.State state = type.createState(instance, config);
        state.variant = Identifier.fromNamespaceAndPath("neroevents", variant);
        state.rotation = Rotation.NONE;
        assertTrue(type.onStart(ctx, config, state).started());
        return new Running(type, state, world, ctx, clock);
    }

    @Test
    void zeroCratesPlacedIsFailure() {
        // Deny the crate's column of wreck_pod_shell (layout x 2, z 2; origin x = anchor − 3 ⇒ anchor x − 1).
        int crateX = ANCHOR.getX() - 3 + 2;
        int crateZ = ANCHOR.getZ() - 3 + 2;
        Running run = start("wreck_pod_shell", List.of(new DenyInBoxSource("claim", crateX, crateZ, crateX, crateZ)));
        WreckPlacer.Result result = run.type().land(run.world(), DebrisCrashPayload.Config.DEFAULT, run.state(), ANCHOR,
                run.clock().now());
        assertTrue(result.crates().isEmpty());
        assertFalse(result.cells().isEmpty());
        assertEquals(TickResult.resolved(EventOutcome.FAILURE),
                run.type().tick(run.ctx(), DebrisCrashPayload.Config.DEFAULT, run.state()));
        assertEquals(1, run.world().records.size(), "the placed cells still decay");
    }

    @Test
    void openingEveryCrateResolvesSuccessAfterTheGrace() {
        Running run = start("wreck_cargo_spill", List.of());
        DebrisCrashPayload.Config config = DebrisCrashPayload.Config.DEFAULT;
        WreckPlacer.Result result = run.type().land(run.world(), config, run.state(), ANCHOR, run.clock().now());
        assertEquals(2, result.crates().size());
        UUID id = run.ctx().instance().id();
        for (BlockPos crate : result.crates()) {
            DebrisCrashPayload.onCrateOpened(id, crate, CoreFixtures.ALICE);
        }
        assertEquals(2, run.state().pending().size(), "one once-keyed credit per crate");
        assertTrue(run.type().tick(run.ctx(), config, run.state()) instanceof TickResult.Continue);
        run.clock().advance(DebrisCrashPayload.ALL_OPENED_GRACE_TICKS);
        assertEquals(TickResult.resolved(EventOutcome.SUCCESS), run.type().tick(run.ctx(), config, run.state()));
    }

    @Test
    void protectedFootprintSkipsAtStart() {
        FakeDebris world = new FakeDebris(view);
        DebrisCrashPayload type = new DebrisCrashPayload((server, dim) -> world, EngineSettings::defaults,
                new ProtectionQuery(List.of()), () -> 0L);
        DebrisCrashPayload.Config config = DebrisCrashPayload.Config.DEFAULT;
        EventInstance instance = CoreFixtures.instance(CoreFixtures.definition(type, config, 12000, 64), ANCHOR, 3L);
        var ctx = CoreFixtures.context(instance, view, new FakeClock(0L),
                List.of(new DenyInBoxSource("claim", ANCHOR.getX(), ANCHOR.getZ(), ANCHOR.getX(), ANCHOR.getZ())));
        DebrisCrashPayload.State state = type.createState(instance, config);
        assertEquals("protected", type.onStart(ctx, config, state).skipReason());
    }
}
