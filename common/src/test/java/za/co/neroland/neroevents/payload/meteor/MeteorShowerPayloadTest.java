package za.co.neroland.neroevents.payload.meteor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.event.EnginePayloadContext;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.event.world.WorldView;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.protect.ProtectionSource;
import za.co.neroland.neroevents.testing.FakeClock;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.TestSources.DenyInBoxSource;
import za.co.neroland.neroevents.testing.core.CoreFixtures;

/**
 * The meteor shower over fake worlds — including the Stage 3 gate: a meteor impact over a denied position
 * places nothing (not even the pocket).
 */
class MeteorShowerPayloadTest {

    private static final BlockPos ANCHOR = new BlockPos(0, 64, 0);
    private static final BlockPos IMPACT = new BlockPos(200, 64, 200);

    /** Records every planned cell set; every cell is replaceable and solid unless told otherwise. */
    static final class RecordingCrater implements CraterBuilder.CraterWorld {
        final List<CraterPlan.Cell> placed = new ArrayList<>();

        @Override
        public boolean replaceable(BlockPos pos) {
            return true;
        }

        @Override
        public boolean solid(BlockPos pos) {
            return true;
        }

        @Override
        public void set(CraterPlan.Cell cell) {
            placed.add(cell);
        }
    }

    /** A scriptable meteor world. */
    static final class FakeMeteors implements MeteorShowerPayload.MeteorWorld {
        final FakeWorldView view;
        final RecordingCrater crater = new RecordingCrater();
        final Set<UUID> loaded = new HashSet<>();
        final List<BlockPos> flashes = new ArrayList<>();
        MeteorShowerPayload.BlockEntityScan scan = MeteorShowerPayload.BlockEntityScan.NONE;
        int spawned;

        FakeMeteors(FakeWorldView view) {
            this.view = view;
        }

        @Override
        public UUID spawnMeteor(UUID instance, int index, double x, double y, double z, double vx, double vy,
                double vz, int fallTicks) {
            spawned++;
            UUID id = new UUID(7L, index);
            loaded.add(id);
            return id;
        }

        @Override
        public boolean meteorLoaded(UUID uuid) {
            return loaded.contains(uuid);
        }

        @Override
        public void discard(UUID uuid) {
            loaded.remove(uuid);
        }

        @Override
        public MeteorShowerPayload.BlockEntityScan scanBlockEntities(Footprint box) {
            return scan;
        }

        @Override
        public CraterBuilder.CraterWorld craterWorld(MeteorShowerPayload.State state, MeteorShowerPayload.Config config,
                long seed) {
            return crater;
        }

        @Override
        public WorldView view() {
            return view;
        }

        @Override
        public void impactFeedback(BlockPos pos, boolean soft, int severityOrdinal) {
            flashes.add(pos);
        }
    }

    private FakeWorldView view;
    private FakeMeteors world;
    private EngineSettings settings;
    private FakeClock clock;

    @BeforeEach
    void setUp() {
        MeteorShowerPayload.resetForTests();
        view = new FakeWorldView().allChunksLoaded(CoreFixtures.DIM);
        world = new FakeMeteors(view);
        settings = EngineSettings.defaults();
        clock = new FakeClock(5000L);
    }

    @AfterEach
    void tearDown() {
        MeteorShowerPayload.resetForTests();
    }

    private MeteorShowerPayload type(List<ProtectionSource> sources) {
        return new MeteorShowerPayload((server, dim) -> world, () -> settings, new ProtectionQuery(sources), id -> true);
    }

    /** Started instance of {@code type} with {@code config}; returns the state. */
    private MeteorShowerPayload.State start(MeteorShowerPayload type, MeteorShowerPayload.Config config,
            List<EnginePayloadContext> ctxOut) {
        EventDefinition def = CoreFixtures.definition(type, config, 12000, 160);
        EventInstance instance = CoreFixtures.instance(def, ANCHOR, 77L);
        EnginePayloadContext ctx = CoreFixtures.context(instance, view, clock);
        MeteorShowerPayload.State state = type.createState(instance, config);
        assertTrue(type.onStart(ctx, config, state).started());
        ctxOut.add(ctx);
        return state;
    }

    private static MeteorShowerPayload.Config config(int count, int perChunk, int perEvent, boolean softOnly) {
        MeteorShowerPayload.Config d = MeteorShowerPayload.Config.DEFAULT;
        return new MeteorShowerPayload.Config(count, d.cadenceJitter(), d.spawnHeight(), d.fallBlocksPerTick(),
                d.craterRadiusMin(), d.craterRadiusMax(), perChunk, perEvent, d.orePerCrater(), d.oreBlocks(),
                d.pocketLootTable(), d.pocketLootBudget(), softOnly);
    }

    // --- the gate --------------------------------------------------------------------------------------

    @Test
    void meteorImpactOverADeniedPositionPlacesNothing() {
        DenyInBoxSource deny = new DenyInBoxSource("claim", IMPACT.getX(), IMPACT.getZ(), IMPACT.getX(), IMPACT.getZ());
        MeteorShowerPayload type = type(List.of(deny));
        MeteorShowerPayload.Config config = MeteorShowerPayload.Config.DEFAULT;
        MeteorShowerPayload.State state = start(type, config, new ArrayList<>());
        MeteorShowerPayload.ImpactOutcome outcome = type.impact(world, config, state, 0, IMPACT);
        assertTrue(outcome.soft());
        assertFalse(outcome.placed().placedAnything());
        assertTrue(world.crater.placed.isEmpty(), "not even the pocket");
        assertEquals(1, state.impactsDone());
        assertEquals(0, state.craters());
    }

    @Test
    void aDenyOnlyOnTheCraterBoxIsASoftLandingWithExactlyOnePocket() {
        // The crater box reaches at least r+1 = 3 blocks out; deny a column 3 east of the impact only.
        DenyInBoxSource deny = new DenyInBoxSource("claim", IMPACT.getX() + 3, IMPACT.getZ(), IMPACT.getX() + 3,
                IMPACT.getZ());
        MeteorShowerPayload type = type(List.of(deny));
        MeteorShowerPayload.Config config = MeteorShowerPayload.Config.DEFAULT;
        MeteorShowerPayload.State state = start(type, config, new ArrayList<>());
        MeteorShowerPayload.ImpactOutcome outcome = type.impact(world, config, state, 0, IMPACT);
        assertTrue(outcome.soft());
        assertEquals(List.of(new CraterPlan.Cell(IMPACT.above(), CraterPlan.Kind.POCKET, -1)), world.crater.placed);
    }

    @Test
    void anAllowedImpactCarvesACrater() {
        MeteorShowerPayload type = type(List.of());
        MeteorShowerPayload.Config config = MeteorShowerPayload.Config.DEFAULT;
        MeteorShowerPayload.State state = start(type, config, new ArrayList<>());
        MeteorShowerPayload.ImpactOutcome outcome = type.impact(world, config, state, 0, IMPACT);
        assertFalse(outcome.soft());
        assertTrue(outcome.placed().craterBlocks() > 0);
        assertTrue(outcome.placed().pocketPlaced());
        assertEquals(1, state.craters());
        assertEquals(List.of(IMPACT), world.flashes);
    }

    @Test
    void meteorBlockDamageOffIsSoft() {
        settings.meteorBlockDamage = false;
        MeteorShowerPayload type = type(List.of());
        MeteorShowerPayload.State state = start(type, MeteorShowerPayload.Config.DEFAULT, new ArrayList<>());
        assertTrue(type.impact(world, MeteorShowerPayload.Config.DEFAULT, state, 0, IMPACT).soft());
        assertEquals(1, world.crater.placed.size());
    }

    @Test
    void destructiveKillSwitchAndSoftOnlyAreSoft() {
        settings.destructiveEventsEnabled = false;
        MeteorShowerPayload type = type(List.of());
        MeteorShowerPayload.State state = start(type, MeteorShowerPayload.Config.DEFAULT, new ArrayList<>());
        assertTrue(type.impact(world, MeteorShowerPayload.Config.DEFAULT, state, 0, IMPACT).soft());
        settings.destructiveEventsEnabled = true;
        MeteorShowerPayload.Config softOnly = config(24, 2, 16, true);
        MeteorShowerPayload.State second = start(type, softOnly, new ArrayList<>());
        assertTrue(type.impact(world, softOnly, second, 1, IMPACT).soft());
    }

    @Test
    void blockEntityNearbyOrUnloadedChunkIsSoft() {
        MeteorShowerPayload type = type(List.of());
        MeteorShowerPayload.State state = start(type, MeteorShowerPayload.Config.DEFAULT, new ArrayList<>());
        world.scan = MeteorShowerPayload.BlockEntityScan.HIT;
        assertTrue(type.impact(world, MeteorShowerPayload.Config.DEFAULT, state, 0, IMPACT).soft());
        world.scan = MeteorShowerPayload.BlockEntityScan.UNLOADED;
        assertTrue(type.impact(world, MeteorShowerPayload.Config.DEFAULT, state, 1, IMPACT.east(64)).soft());
        settings.craterAvoidsBlockEntities = false;
        assertFalse(type.impact(world, MeteorShowerPayload.Config.DEFAULT, state, 2, IMPACT.east(128)).soft());
    }

    @Test
    void perChunkAndPerEventCapsMakeLaterImpactsSoft() {
        MeteorShowerPayload type = type(List.of());
        MeteorShowerPayload.Config perChunk = config(24, 1, 16, false);
        MeteorShowerPayload.State a = start(type, perChunk, new ArrayList<>());
        assertFalse(type.impact(world, perChunk, a, 0, IMPACT).soft());
        assertTrue(type.impact(world, perChunk, a, 1, IMPACT.east(2)).soft(), "same chunk");
        assertFalse(type.impact(world, perChunk, a, 2, IMPACT.east(64)).soft(), "another chunk");

        MeteorShowerPayload.Config perEvent = config(24, 8, 1, false);
        MeteorShowerPayload.State b = start(type, perEvent, new ArrayList<>());
        assertFalse(type.impact(world, perEvent, b, 0, IMPACT).soft());
        assertTrue(type.impact(world, perEvent, b, 1, IMPACT.east(64)).soft());
    }

    // --- scheduling and resolution ---------------------------------------------------------------------

    @Test
    void scheduleSpansTheDuration() {
        MeteorShowerPayload.Config config = MeteorShowerPayload.Config.DEFAULT;
        long[] at = MeteorShowerPayload.schedule(1000L, 12000, config, new Random(3L));
        assertEquals(config.meteorCount(), at.length);
        assertEquals(1020L, at[0]);
        long span = 12000L - config.fallTicks() - 40L;
        for (int i = 1; i < at.length; i++) {
            assertTrue(at[i] >= at[i - 1]);
            assertTrue(at[i] <= 1020L + span);
        }
        assertTrue(at[at.length - 1] > 1020L + span / 2, "spread over the duration");
    }

    @Test
    void atMostThreeSpawnsPerTick() {
        MeteorShowerPayload type = type(List.of());
        List<EnginePayloadContext> ctx = new ArrayList<>();
        MeteorShowerPayload.Config config = MeteorShowerPayload.Config.DEFAULT;
        MeteorShowerPayload.State state = start(type, config, ctx);
        clock.advance(20000L); // every spawn is due
        type.tick(ctx.get(0), config, state);
        assertEquals(3, state.nextIndex());
        assertEquals(3, world.spawned);
        type.tick(ctx.get(0), config, state);
        assertEquals(6, state.nextIndex());
    }

    @Test
    void impactsPlusSkippedReachingTheCountIsSuccess() {
        MeteorShowerPayload type = type(List.of());
        List<EnginePayloadContext> ctx = new ArrayList<>();
        MeteorShowerPayload.Config config = config(2, 2, 16, false);
        MeteorShowerPayload.State state = start(type, config, ctx);
        clock.advance(20000L);
        assertInstanceOf(TickResult.Continue.class, type.tick(ctx.get(0), config, state));
        assertEquals(2, state.inFlight().size());
        type.impact(world, config, state, 0, IMPACT);
        MeteorShowerPayload.onLost(ctx.get(0).instance().id(), 1, new UUID(7L, 1));
        world.loaded.clear();
        assertEquals(TickResult.resolved(EventOutcome.SUCCESS), type.tick(ctx.get(0), config, state));
        assertEquals(1, state.impactsDone());
        assertEquals(1, state.skipped());
    }

    @Test
    void unloadedTargetIsRerolledFourTimesThenSkipped() {
        view = new FakeWorldView(); // nothing loaded
        world = new FakeMeteors(view);
        MeteorShowerPayload type = type(List.of());
        List<EnginePayloadContext> ctx = new ArrayList<>();
        MeteorShowerPayload.Config config = config(1, 2, 16, false);
        MeteorShowerPayload.State state = start(type, config, ctx);
        clock.advance(20000L);
        int before = view.isChunkLoadedCalls;
        type.tick(ctx.get(0), config, state);
        assertEquals(5, view.isChunkLoadedCalls - before, "the first roll plus four re-rolls");
        assertEquals(0, world.spawned);
        assertEquals(1, state.skipped());
    }

    @Test
    void timeoutIsSuccessOnlyAfterAnImpact() {
        MeteorShowerPayload type = type(List.of());
        List<EnginePayloadContext> ctx = new ArrayList<>();
        MeteorShowerPayload.Config config = MeteorShowerPayload.Config.DEFAULT;
        MeteorShowerPayload.State state = start(type, config, ctx);
        assertEquals(EventOutcome.EXPIRED, type.onTimeout(ctx.get(0), config, state));
        type.impact(world, config, state, 0, IMPACT);
        assertEquals(EventOutcome.SUCCESS, type.onTimeout(ctx.get(0), config, state));
    }

    @Test
    void pocketMinedAfterTheInstanceIsGoneCreditsNobody() {
        MeteorShowerPayload type = type(List.of());
        List<EnginePayloadContext> ctx = new ArrayList<>();
        MeteorShowerPayload.Config config = MeteorShowerPayload.Config.DEFAULT;
        MeteorShowerPayload.State state = start(type, config, ctx);
        UUID id = ctx.get(0).instance().id();
        MeteorShowerPayload.onPocketMined(id, IMPACT, CoreFixtures.ALICE);
        assertEquals(1, state.pending().size());
        assertEquals("pocket:" + IMPACT.asLong(), state.pending().snapshot().get(0).onceKey());
        type.onCleanup(ctx.get(0), config, state);
        MeteorShowerPayload.onPocketMined(id, IMPACT, CoreFixtures.BOB);
        assertEquals(0, state.pending().size());
    }

    @Test
    void saveLoadRoundTrip() {
        MeteorShowerPayload type = type(List.of());
        List<EnginePayloadContext> ctx = new ArrayList<>();
        MeteorShowerPayload.Config config = MeteorShowerPayload.Config.DEFAULT;
        MeteorShowerPayload.State state = start(type, config, ctx);
        clock.advance(20000L);
        type.tick(ctx.get(0), config, state);
        type.impact(world, config, state, 0, IMPACT);
        var tag = type.saveState(state);
        MeteorShowerPayload.State loaded = type.loadState(ctx.get(0).instance(), config, tag);
        assertEquals(tag, type.saveState(loaded));
        assertEquals(state.inFlight(), loaded.inFlight());
        assertTrue(type.onResume(ctx.get(0), config, loaded).resumed());
    }

    @Test
    void invertedRadiiWarnAndFallBack() {
        MeteorShowerPayload.Config d = MeteorShowerPayload.Config.DEFAULT;
        MeteorShowerPayload.Config bad = new MeteorShowerPayload.Config(d.meteorCount(), d.cadenceJitter(),
                d.spawnHeight(), d.fallBlocksPerTick(), 4, 2, 2, 16, 2, List.of(Identifier.parse("minecraft:stone")),
                d.pocketLootTable(), d.pocketLootBudget(), false);
        assertEquals(1, type(List.of()).validate(bad).size());
        assertEquals(2, bad.radiusMin());
        assertEquals(4, bad.radiusMax());
    }

    @Test
    void oreBlocksThatAreNotPlainSolidAreWarnedAndIgnored() {
        Identifier stone = Identifier.parse("minecraft:stone");
        Identifier lava = Identifier.parse("minecraft:lava");
        MeteorShowerPayload.Config d = MeteorShowerPayload.Config.DEFAULT;
        MeteorShowerPayload.Config config = new MeteorShowerPayload.Config(d.meteorCount(), d.cadenceJitter(),
                d.spawnHeight(), d.fallBlocksPerTick(), d.craterRadiusMin(), d.craterRadiusMax(), 2, 16, 2,
                List.of(stone, lava), d.pocketLootTable(), d.pocketLootBudget(), false);
        MeteorShowerPayload type = new MeteorShowerPayload((server, dim) -> world, () -> settings,
                new ProtectionQuery(List.of()), id -> true, id -> !id.equals(lava));
        List<String> warnings = type.validate(config);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("minecraft:lava") && warnings.get(0).contains("plain solid"));
    }
}
