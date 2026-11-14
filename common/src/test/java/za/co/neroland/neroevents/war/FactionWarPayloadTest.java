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

    private static za.co.neroland.neroevents.event.SchedulerSnapshot snapshot(boolean factionsLoaded,
            boolean singlePlayer) {
        java.util.Set<String> mods = factionsLoaded ? java.util.Set.of(
                za.co.neroland.neroevents.compat.factions.FactionsCompat.MOD_ID) : java.util.Set.of();
        int online = singlePlayer ? 1 : 4;
        return new za.co.neroland.neroevents.event.SchedulerSnapshot(0L, online,
                za.co.neroland.neroevents.event.BandTable.DEFAULT.bandFor(online), false,
                za.co.neroland.neroevents.event.ConsentMode.OFF, !singlePlayer, singlePlayer, false,
                java.util.Map.of(), List.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), mods,
                java.util.Map.of(), java.util.Map.of(), 0, 0L, java.util.Map.of(), java.util.Map.of(), Long.MAX_VALUE);
    }

    @Test
    void preflightExcludesSinglePlayerWithAReason() {
        assertEquals(java.util.Optional.of("no_factions"), payload.preflight(snapshot(false, false), definition));
        assertEquals(java.util.Optional.of("no_factions"), payload.preflight(snapshot(false, true), definition));
        assertEquals(java.util.Optional.of("single_player"), payload.preflight(snapshot(true, true), definition));
        assertEquals(java.util.Optional.empty(), payload.preflight(snapshot(true, false), definition));
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
        assertEquals(List.of("red:10"), world.awards, "the victor is paid through NeroFactions");
        assertEquals(List.of("blue:-5"), reputation.lines, "the loser through Core; the victor never twice");
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
        assertEquals(List.of("red:10"), world.awards, "the deciding capturer earns reputation");
        assertTrue(reputation.lines.isEmpty());
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

    // --- NeroFactions victory awards -----------------------------------------------------------

    /** A started 2 v 2 war (one beacon) that red has won on score, red's capturer credited; blue is side A. */
    private FactionWarPayload.State wonByRed(PayloadContext c, FactionWarPayload.Config cfg, UUID redCapturer) {
        world.faction(RED, redCapturer, UUID.randomUUID()).faction(BLUE, players(2));
        FactionWarPayload.State state = payload.createState(null, cfg);
        assertTrue(payload.onStart(c, cfg, state).started());
        world.presence.put(state.objectives().get(0).pos(), List.of(redCapturer));
        for (int t = 0; t < 200; t++) {
            payload.tick(c, cfg, state);
        }
        c.contributions().credit(redCapturer, ContributionTable.Kind.OBJECTIVE, 1.0D);
        return state;
    }

    private static final FactionWarPayload.Config ONE_BEACON =
            new FactionWarPayload.Config(1, 48, 6, 200, 10, 0, 0, 10, 5);

    @Test
    void oneEligibleOutcomeAwardsOnceWithTheConfiguredAmountAndReportsWhatWasApplied() {
        PayloadContext c = ctx();
        FactionWarPayload.State state = wonByRed(c, ONE_BEACON, UUID.randomUUID());
        world.awardAnswer = 7; // NeroFactions applied less than asked (near the daily cap)
        assertEquals(EventOutcome.SUCCESS, payload.onTimeout(c, ONE_BEACON, state));
        assertEquals(List.of("red:10"), world.awards, "reputation_win, capped by the per-event cap (25)");
        assertEquals(List.of(FactionWarPayload.MSG_REPUTATION_GAINED + "|7|red"), world.told,
                "the feedback uses the applied amount, not the asked one");
        assertEquals("neroevents:war_victory", FactionWarPayload.REASON_WAR_VICTORY.toString());
    }

    @Test
    void perEventCapAndZeroWinTurnTheAwardDownOrOff() {
        FactionWarPayload.useForTests(ctx -> world, true, 2, 4, reputation);
        PayloadContext c = ctx();
        FactionWarPayload.State state = wonByRed(c, ONE_BEACON, UUID.randomUUID());
        payload.onTimeout(c, ONE_BEACON, state);
        assertEquals(List.of("red:4"), world.awards);

        FactionWarPayload.resetForTests();
        FactionWarPayload.useForTests(ctx -> world, true, 2, 25, reputation);
        world.awards.clear();
        FactionWarPayload.Config off = new FactionWarPayload.Config(1, 48, 6, 200, 10, 0, 0, 0, 5);
        PayloadContext c2 = ctx();
        FactionWarPayload.State state2 = wonByRed(c2, off, UUID.randomUUID());
        assertEquals(EventOutcome.SUCCESS, payload.onTimeout(c2, off, state2));
        assertTrue(world.awards.isEmpty(), "reputation_win 0 awards nothing");
    }

    @Test
    void repeatedOrReTickedOutcomeAwardsNothingMore() {
        PayloadContext c = ctx();
        EventInstance instance = c.instance();
        FactionWarPayload.State state = wonByRed(c, ONE_BEACON, UUID.randomUUID());
        assertEquals(EventOutcome.SUCCESS, payload.onTimeout(c, ONE_BEACON, state));
        assertEquals(EventOutcome.SUCCESS, payload.onTimeout(c, ONE_BEACON, state), "a repeated timeout");
        assertTrue(payload.tick(c, ONE_BEACON, state) instanceof TickResult.Resolved, "a re-tick after resolving");
        assertEquals(EventOutcome.SUCCESS, FactionWarPayload.resolveWar(c, ONE_BEACON, state));
        assertEquals(1, world.awards.size());
        assertEquals(1, world.told.size());

        // A resolved war that is saved and loaded again stays resolved and never pays again.
        FactionWarPayload.State loaded = payload.loadState(instance, ONE_BEACON, payload.saveState(state));
        assertTrue(payload.onResume(ctx(instance), ONE_BEACON, loaded).resumed());
        TickResult again = payload.tick(ctx(instance), ONE_BEACON, loaded);
        assertTrue(again instanceof TickResult.Resolved);
        assertEquals(EventOutcome.SUCCESS, ((TickResult.Resolved) again).outcome());
        assertEquals(EventOutcome.SUCCESS, payload.onTimeout(ctx(instance), ONE_BEACON, loaded));
        assertEquals(1, world.awards.size());
    }

    @Test
    void aPlayerListedTwiceIsPaidOnce() {
        UUID red = UUID.randomUUID();
        world.faction(RED, red);
        List<WarScoring.ReputationLine> lines = List.of(new WarScoring.ReputationLine(red, RED, 10),
                new WarScoring.ReputationLine(red, RED, 10));
        assertEquals(1, FactionWarPayload.awardVictors(world, lines, "Red"));
        assertEquals(List.of("red:10"), world.awards);
    }

    @Test
    void unknownFactionIdAwardsNothingAndSaysNothing() {
        PayloadContext c = ctx();
        FactionWarPayload.State state = wonByRed(c, ONE_BEACON, UUID.randomUUID());
        world.factions.remove(RED); // NeroFactions no longer defines red (a datapack reload mid-war)
        assertEquals(EventOutcome.SUCCESS, payload.onTimeout(c, ONE_BEACON, state));
        assertTrue(world.awards.isEmpty());
        assertTrue(world.told.isEmpty());

        UUID stranger = UUID.randomUUID();
        Identifier unknown = Identifier.fromNamespaceAndPath("test", "unknown");
        assertEquals(0, FactionWarPayload.awardVictors(world,
                List.of(new WarScoring.ReputationLine(stranger, unknown, 10)), "Unknown"));
        assertTrue(world.awards.isEmpty());
    }

    @Test
    void eventCapReachedReportsNothingGainedWithoutFailing() {
        PayloadContext c = ctx();
        FactionWarPayload.State state = wonByRed(c, ONE_BEACON, UUID.randomUUID());
        world.awardAnswer = 0; // the player's daily EVENT cap is reached
        assertEquals(EventOutcome.SUCCESS, payload.onTimeout(c, ONE_BEACON, state));
        assertEquals(List.of("red:10"), world.awards, "asked once");
        assertEquals(List.of(FactionWarPayload.MSG_REPUTATION_NONE + "|red"), world.told);
        assertEquals(2, fired.size(), "war_resolved still fires for both factions");
    }

    @Test
    void aFailedAwardSaysNothingAndStillResolves() {
        PayloadContext c = ctx();
        FactionWarPayload.State state = wonByRed(c, ONE_BEACON, UUID.randomUUID());
        world.awardAnswer = -1; // NeroFactions could not be asked
        assertEquals(EventOutcome.SUCCESS, payload.onTimeout(c, ONE_BEACON, state));
        assertTrue(world.told.isEmpty());
        assertEquals(0, FactionWarPayload.awardVictors(null,
                List.of(new WarScoring.ReputationLine(UUID.randomUUID(), RED, 10)), "Red"), "no world, no award");
    }
}
