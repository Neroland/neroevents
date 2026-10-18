package za.co.neroland.neroevents.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.neroevents.event.ContributionTable;
import za.co.neroland.neroevents.event.EnginePayloadContext;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.ResumeResult;
import za.co.neroland.neroevents.event.StartResult;
import za.co.neroland.neroevents.event.TestInstances;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.protect.ProtectionSource;
import za.co.neroland.neroevents.testing.FakeClock;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.TestSources;
import za.co.neroland.neroevents.testing.war.FakeWarWorld;

/** Eligibility, objective placement under protection, resume, and resolution of {@code neroevents:faction_war}. */
class FactionWarPayloadTest {

    private static final Identifier RED = WarSidesTest.RED;
    private static final Identifier BLUE = WarSidesTest.BLUE;
    private static final Identifier GREEN = WarSidesTest.GREEN;

    /** Records reputation changes; "real" provider on demand. */
    static final class RecordingReputation implements FactionWarPayload.Reputation {
        boolean real = true;
        final List<String> lines = new ArrayList<>();

        @Override
        public boolean hasRealProvider() {
            return real;
        }

        @Override
        public void adjust(UUID player, Identifier faction, int delta) {
            lines.add(faction.getPath() + ":" + delta);
        }
    }

    private final FactionWarPayload payload = new FactionWarPayload();
    private final FakeWarWorld world = new FakeWarWorld();
    private final RecordingReputation reputation = new RecordingReputation();
    private final FakeClock clock = new FakeClock(0L);
    private final List<ThresholdEvents.ThresholdCrossing> fired = new ArrayList<>();
    private EventDefinition definition;

    @BeforeEach
    void setUp() {
        FactionWarPayload.useForTests(ctx -> world, true, 2, 25, reputation);
        WarChannel.useForTests(fired::add);
        definition = TestInstances.definition("faction_war_test", "war", null);
    }

    @AfterEach
    void tearDown() {
        FactionWarPayload.resetForTests();
        FactionWarPayload.useDrainForTests(null);
        WarChannel.useForTests(null);
    }

    private PayloadContext ctx(ProtectionSource... sources) {
        EventInstance instance = TestInstances.instance(definition, null);
        return new EnginePayloadContext(instance, new FakeWorldView(), new ProtectionQuery(List.of(sources)), clock);
    }

    private PayloadContext ctx(EventInstance instance, ProtectionSource... sources) {
        return new EnginePayloadContext(instance, new FakeWorldView(), new ProtectionQuery(List.of(sources)), clock);
    }

    private static UUID[] players(int n) {
        UUID[] out = new UUID[n];
        for (int i = 0; i < n; i++) {
            out[i] = UUID.randomUUID();
        }
        return out;
    }

    @Test
    void fewerThanTwoEligibleFactionsSkips() {
        world.faction(RED, players(2)).faction(BLUE, players(1));
        FactionWarPayload.Config cfg = FactionWarPayload.Config.DEFAULTS;
        StartResult result = payload.onStart(ctx(), cfg, payload.createState(null, cfg));
        assertEquals("no_factions", result.skipReason());
        assertEquals(0, world.placements);
    }

    @Test
    void unavailableBridgeSkips() {
        world.faction(RED, players(2)).faction(BLUE, players(2));
        FactionWarPayload.useForTests(ctx -> world, false, 2, 25, reputation);
        FactionWarPayload.Config cfg = FactionWarPayload.Config.DEFAULTS;
        assertEquals("no_factions", payload.onStart(ctx(), cfg, payload.createState(null, cfg)).skipReason());
    }

    @Test
    void eligibilityFollowsWarMinMembersOnline() {
        world.faction(RED, players(3)).faction(BLUE, players(2)).faction(GREEN, players(2));
        FactionWarPayload.useForTests(ctx -> world, true, 3, 25, reputation);
        FactionWarPayload.Config cfg = FactionWarPayload.Config.DEFAULTS;
        assertEquals("no_factions", payload.onStart(ctx(), cfg, payload.createState(null, cfg)).skipReason());

        FactionWarPayload.useForTests(ctx -> world, true, 2, 25, reputation);
        FactionWarPayload.State state = payload.createState(null, cfg);
        assertTrue(payload.onStart(ctx(), cfg, state).started());
        assertEquals(RED, state.sideA(), "most members online first");
        assertEquals(BLUE, state.sideB(), "ties broken by id (blue < green)");
        assertEquals(3, state.objectives().size());
    }

    @Test
    void objectivesArePlacedOnlyWhereProtectionAllows() {
        world.faction(RED, players(2)).faction(BLUE, players(2));
        FactionWarPayload.Config cfg = FactionWarPayload.Config.DEFAULTS;
        FactionWarPayload.State state = payload.createState(null, cfg);
        // Everything whose footprint touches x >= 0 is denied.
        PayloadContext c = ctx(new TestSources.DenyInBoxSource("box", 0, -1000, 1000, 1000));
        assertTrue(payload.onStart(c, cfg, state).started());
        assertFalse(state.objectives().isEmpty());
        for (WarScoring.Objective o : state.objectives()) {
            assertTrue(o.pos().getX() + FactionWarPayload.OBJECTIVE_FOOTPRINT_RADIUS < 0,
                    "objective at x " + o.pos().getX() + " overlaps the denied box");
        }
        assertEquals(state.objectives().size(), world.beacons.size());
    }

    @Test
    void nothingPlaceableSkipsProtected() {
        world.faction(RED, players(2)).faction(BLUE, players(2));
        FactionWarPayload.Config cfg = FactionWarPayload.Config.DEFAULTS;
        StartResult result = payload.onStart(ctx(new TestSources.DenyAllSource("all")), cfg,
                payload.createState(null, cfg));
        assertEquals("protected", result.skipReason());
        assertEquals(0, world.placements);
    }

    @Test
    void resumeRePlacesAMissingBeaconOrRefuses() {
        world.faction(RED, players(2)).faction(BLUE, players(2));
        FactionWarPayload.Config cfg = FactionWarPayload.Config.DEFAULTS;
        FactionWarPayload.State started = payload.createState(null, cfg);
        EventInstance instance = TestInstances.instance(definition, null);
        assertTrue(payload.onStart(ctx(instance), cfg, started).started());
        FactionWarPayload.State loaded = payload.loadState(instance, cfg, payload.saveState(started));
        assertEquals(3, loaded.objectives().size());

        BlockPos lost = world.beacons.keySet().iterator().next();
        world.beacons.remove(lost);
        int before = world.placements;
        ResumeResult ok = payload.onResume(ctx(instance), cfg, loaded);
        assertTrue(ok.resumed());
        assertEquals(before + 1, world.placements);
        assertEquals(3, loaded.objectives().size());

        FactionWarPayload.State again = payload.loadState(instance, cfg, payload.saveState(loaded));
        world.beacons.clear();
        ResumeResult refused = payload.onResume(ctx(instance, new TestSources.DenyAllSource("all")), cfg, again);
        assertFalse(refused.resumed());
        assertEquals("objectives_lost", refused.cancelReason());
    }

    @Test
    void timeoutResolvesWithReputationAndWarResolved() {
        UUID[] reds = players(2);
        UUID[] blues = players(2);
        world.faction(RED, reds).faction(BLUE, blues);
        FactionWarPayload.Config cfg = new FactionWarPayload.Config(1, 48, 6, 200, 10, 0, 0, 10, 5);
        FactionWarPayload.State state = payload.createState(null, cfg);
        PayloadContext c = ctx();
        assertTrue(payload.onStart(c, cfg, state).started());
        BlockPos beacon = state.objectives().get(0).pos();
        world.presence.put(beacon, List.of(reds[0]));
        for (int t = 0; t < 200; t++) {
            assertFalse(payload.tick(c, cfg, state) instanceof TickResult.Resolved);
        }
        assertEquals(1, world.captured.size());
        // 2 v 2 online: sides are ordered by members online, then by id (blue < red), so red is side B.
        assertEquals(BLUE, state.sideA());
        assertEquals(RED, state.sideB());
        assertEquals(10, state.scoring().score(WarSides.Side.B));
        assertEquals(0, state.scoring().score(WarSides.Side.A));
        assertEquals(1, state.pending().size(), "the capturer is queued for OBJECTIVE credit");
        // Credit lands in the table on a running server; here it is applied directly.
        c.contributions().credit(reds[0], ContributionTable.Kind.OBJECTIVE, 1.0D);
        state.sides().sideOf(blues[0], world::membershipOf);
        c.contributions().credit(blues[0], ContributionTable.Kind.OBJECTIVE, 1.0D);

        assertEquals(EventOutcome.SUCCESS, payload.onTimeout(c, cfg, state));
        assertEquals(List.of("red:10", "blue:-5"), reputation.lines.stream().sorted((x, y) -> y.compareTo(x)).toList());
        assertEquals(2, fired.size());
        assertTrue(fired.stream().anyMatch(f -> f.scope().equals(RED.toString()) && f.rising()));
        assertTrue(fired.stream().anyMatch(f -> f.scope().equals(BLUE.toString()) && !f.rising()));
        assertEquals(EventOutcome.SUCCESS, payload.onTimeout(c, cfg, state), "resolves once");
        assertEquals(2, fired.size());

        payload.onCleanup(c, cfg, state);
        assertTrue(world.beacons.isEmpty());
    }

    /** Lands queued credits straight in the context's table, the way a running server's drain would. */
    private static void tableDrain(PayloadContext ctx, za.co.neroland.neroevents.payload.PendingCredits pending) {
        pending.drainInto(uuid -> uuid, new za.co.neroland.neroevents.payload.PendingCredits.Sink<UUID>() {
            @Override
            public boolean loot(UUID player, String onceKey) {
                return false;
            }

            @Override
            public boolean supplyClaim(UUID player, UUID podUuid) {
                return false;
            }

            @Override
            public boolean objective(UUID player, double fraction) {
                ctx.contributions().credit(player, ContributionTable.Kind.OBJECTIVE, fraction);
                return true;
            }

            @Override
            public boolean survival(UUID player, int waveIndex) {
                return false;
            }

            @Override
            public boolean damage(net.minecraft.world.damagesource.DamageSource source, float amount) {
                return false;
            }
        });
    }

    @Test
    void captureInTheWinningPassEarnsReputation() {
        FactionWarPayload.useDrainForTests(FactionWarPayloadTest::tableDrain);
        UUID[] reds = players(2);
        world.faction(RED, reds).faction(BLUE, players(2));
        // win_score 10 = one capture: the pass that captures is the pass that wins.
        FactionWarPayload.Config cfg = new FactionWarPayload.Config(1, 48, 6, 200, 10, 0, 10, 10, 5);
        FactionWarPayload.State state = payload.createState(null, cfg);
        PayloadContext c = ctx();
        assertTrue(payload.onStart(c, cfg, state).started());
        world.presence.put(state.objectives().get(0).pos(), List.of(reds[0]));
        TickResult last = null;
        for (int t = 0; t < 400 && !(last instanceof TickResult.Resolved); t++) {
            last = payload.tick(c, cfg, state);
        }
        assertTrue(last instanceof TickResult.Resolved, "the capture reaches win_score");
        assertEquals(EventOutcome.SUCCESS, state.outcome);
        assertEquals(0, state.pending().size(), "the deciding pass's credit was drained before resolving");
        assertEquals(List.of("red:10"), reputation.lines, "the deciding capturer earns reputation");
    }

    @Test
    void noRealProviderMeansNoReputationAndTieMeansEnded() {
        world.faction(RED, players(2)).faction(BLUE, players(2));
        reputation.real = false;
        FactionWarPayload.Config cfg = FactionWarPayload.Config.DEFAULTS;
        FactionWarPayload.State state = payload.createState(null, cfg);
        PayloadContext c = ctx();
        assertTrue(payload.onStart(c, cfg, state).started());
        assertEquals(EventOutcome.ENDED, payload.onTimeout(c, cfg, state));
        assertTrue(reputation.lines.isEmpty());
        assertEquals(2, fired.size());
        assertTrue(fired.stream().noneMatch(ThresholdEvents.ThresholdCrossing::rising));
    }

    @Test
    void forgetErasesFrozenSides() {
        UUID[] reds = players(2);
        world.faction(RED, reds).faction(BLUE, players(2));
        FactionWarPayload.Config cfg = FactionWarPayload.Config.DEFAULTS;
        FactionWarPayload.State state = payload.createState(null, cfg);
        assertTrue(payload.onStart(ctx(), cfg, state).started());
        state.sides().sideOf(reds[0], world::membershipOf);
        state.pending().objective(reds[0], 1.0D);
        FactionWarPayload.forget(reds[0]);
        assertFalse(state.sides().holds(reds[0]));
        assertEquals(0, state.pending().size());
    }
}
