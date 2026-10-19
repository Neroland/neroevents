package za.co.neroland.neroevents.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.event.ConsentMode;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.PopulationBand;
import za.co.neroland.neroevents.event.SchedulerSnapshot;
import za.co.neroland.neroevents.event.StartResult;
import za.co.neroland.neroevents.event.TestInstances;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.testing.FakeClock;
import za.co.neroland.neroevents.testing.FakePayloadContext;
import za.co.neroland.neroevents.testing.FakeWorldView;

/** {@code neroevents:market_shock}: confirm / fail / pause / end / resume, against a synchronous fake market. */
class MarketShockPayloadTest {

    private static final String IRON = "#c:ingots/iron";
    private static final String COPPER = "#c:ingots/copper";

    /** A fake NeroEconomy: optionally confirms inside apply (the synchronous Core dispatch). */
    static final class FakeMarket implements MarketShockPayload.Market {
        boolean confirm = true;
        final Set<String> active = new HashSet<>();
        final List<String> applied = new ArrayList<>();
        final List<Long> minutes = new ArrayList<>();
        final List<String> ended = new ArrayList<>();

        @Override
        public boolean shockActive(String key) {
            return active.contains(key);
        }

        @Override
        public void apply(String key, double multiplier, long mins) {
            applied.add(key);
            minutes.add(mins);
            if (confirm) {
                // NeroEconomy publishes under its own spelling: upper-case + padding must still match.
                MarketShockPayload.onSignal(new EconomySignals.EconomySignal(EconomySignals.Kind.MARKET_SHOCK,
                        " " + key.toUpperCase(java.util.Locale.ROOT), Math.round(multiplier * 1000), 1000, true));
            }
        }

        @Override
        public void end(String key) {
            ended.add(key);
        }
    }

    private final MarketShockPayload payload = new MarketShockPayload();
    private final FakeMarket market = new FakeMarket();
    private final FakeClock clock = new FakeClock(1000L);
    private final long[] wall = {10_000_000L};
    private EventDefinition definition;

    @BeforeEach
    void setUp() {
        MarketShockPayload.resetForTests();
        MarketShockPayload.useForTests(market, () -> wall[0], true);
        definition = TestInstances.definition("market_shock_test", "economy", null);
    }

    @AfterEach
    void tearDown() {
        MarketShockPayload.resetForTests();
    }

    private static MarketShockPayload.Config config(String... keys) {
        List<MarketShockPayload.Shock> shocks = new ArrayList<>();
        for (String key : keys) {
            shocks.add(new MarketShockPayload.Shock(key, 1.5D, 45, "event.neroevents.market_shock.shortage.iron"));
        }
        return new MarketShockPayload.Config(shocks);
    }

    private FakePayloadContext ctx() {
        EventInstance instance = TestInstances.instance(definition, null);
        return new FakePayloadContext(instance, new FakeWorldView(), clock);
    }

    private static boolean resolvedWith(TickResult result, EventOutcome outcome) {
        return result instanceof TickResult.Resolved r && r.outcome() == outcome;
    }

    /** Starts one instance and lets 21 ticks pass. */
    private TickResult startAndWait(MarketShockPayload.Config cfg, MarketShockPayload.State state, FakePayloadContext c) {
        assertTrue(payload.onStart(c, cfg, state).started());
        clock.advance(MarketShockPayload.CONFIRM_TICKS + 1);
        return payload.tick(c, cfg, state);
    }

    private static SchedulerSnapshot snapshot(Set<String> mods) {
        return new SchedulerSnapshot(0L, 1, new PopulationBand("0-1", 0, 1, 1.0D, 1), false, ConsentMode.values()[0],
                true, false, false, Map.of(), List.of(), Map.of(), Map.of(), Map.of(), mods, Map.of(), Map.of(), 0, 0L, Map.of(),
                Map.of(), Long.MAX_VALUE);
    }

    @Test
    void confirmationInsideApplyConfirms() {
        MarketShockPayload.Config cfg = config(IRON);
        MarketShockPayload.State state = payload.createState(null, cfg);
        FakePayloadContext c = ctx();
        assertTrue(payload.onStart(c, cfg, state).started());
        assertTrue(state.confirmed());
        assertEquals(IRON, state.key());
        assertEquals(List.of(IRON), market.applied);
        clock.advance(40);
        assertInstanceOf(TickResult.Continue.class, payload.tick(c, cfg, state));
    }

    @Test
    void unconfirmedWithinTwentyTicksFails() {
        market.confirm = false;
        MarketShockPayload.Config cfg = config(IRON);
        MarketShockPayload.State state = payload.createState(null, cfg);
        FakePayloadContext c = ctx();
        assertTrue(payload.onStart(c, cfg, state).started());
        clock.advance(MarketShockPayload.CONFIRM_TICKS);
        assertInstanceOf(TickResult.Continue.class, payload.tick(c, cfg, state)); // exactly 20: still waiting
        clock.advance(1);
        assertTrue(resolvedWith(payload.tick(c, cfg, state), EventOutcome.FAILURE));
        assertEquals("unconfirmed", state.reason());
        assertFalse(MarketShockPayload.isPaused());
    }

    @Test
    void twoConsecutiveMissesPauseAndThirdIsRefused() {
        market.confirm = false;
        MarketShockPayload.Config cfg = config(IRON);
        assertTrue(resolvedWith(startAndWait(cfg, payload.createState(null, cfg), ctx()), EventOutcome.FAILURE));
        assertFalse(MarketShockPayload.isPaused());
        assertTrue(resolvedWith(startAndWait(cfg, payload.createState(null, cfg), ctx()), EventOutcome.FAILURE));
        assertTrue(MarketShockPayload.isPaused());
        assertEquals(Optional.of("shock_paused"), payload.preflight(snapshot(Set.of("neroeconomy")), definition));
        StartResult third = payload.onStart(ctx(), cfg, payload.createState(null, cfg));
        assertEquals("shock_paused", third.skipReason());
    }

    @Test
    void preflightNeedsNeroEconomy() {
        assertEquals(Optional.of("no_economy"), payload.preflight(snapshot(Set.of()), definition));
        assertEquals(Optional.empty(), payload.preflight(snapshot(Set.of("neroeconomy")), definition));
    }

    @Test
    void economyInactiveSkips() {
        MarketShockPayload.useForTests(market, () -> wall[0], false);
        MarketShockPayload.Config cfg = config(IRON);
        assertEquals("no_economy", payload.onStart(ctx(), cfg, payload.createState(null, cfg)).skipReason());
    }

    @Test
    void confirmedResetsTheStreak() {
        MarketShockPayload.Config cfg = config(IRON);
        market.confirm = false;
        assertTrue(resolvedWith(startAndWait(cfg, payload.createState(null, cfg), ctx()), EventOutcome.FAILURE));
        market.confirm = true;
        MarketShockPayload.State ok = payload.createState(null, cfg);
        FakePayloadContext okCtx = ctx();
        assertInstanceOf(TickResult.Continue.class, startAndWait(cfg, ok, okCtx));
        payload.onCleanup(okCtx, cfg, ok);
        market.confirm = false;
        assertTrue(resolvedWith(startAndWait(cfg, payload.createState(null, cfg), ctx()), EventOutcome.FAILURE));
        assertFalse(MarketShockPayload.isPaused());
    }

    @Test
    void fallingCrossingForTheNormalisedKeyEnds() {
        MarketShockPayload.Config cfg = config(IRON);
        MarketShockPayload.State state = payload.createState(null, cfg);
        FakePayloadContext c = ctx();
        payload.onStart(c, cfg, state);
        MarketShockPayload.onSignal(new EconomySignals.EconomySignal(EconomySignals.Kind.MARKET_SHOCK, COPPER, 1000, 1000,
                false));
        clock.advance(1);
        assertInstanceOf(TickResult.Continue.class, payload.tick(c, cfg, state)); // another key is ignored
        MarketShockPayload.onSignal(new EconomySignals.EconomySignal(EconomySignals.Kind.MARKET_SHOCK, " #C:Ingots/Iron",
                1000, 1000, false));
        clock.advance(1);
        assertTrue(resolvedWith(payload.tick(c, cfg, state), EventOutcome.ENDED));
        payload.onCleanup(c, cfg, state);
        assertTrue(market.ended.isEmpty(), "an ended shock is not ended again");
    }

    @Test
    void otherChannelsAreIgnored() {
        MarketShockPayload.Config cfg = config(IRON);
        market.confirm = false;
        MarketShockPayload.State state = payload.createState(null, cfg);
        payload.onStart(ctx(), cfg, state);
        MarketShockPayload.onSignal(new EconomySignals.EconomySignal(EconomySignals.Kind.SECTOR_INDEX, IRON, 1, 1,
                true));
        assertFalse(state.confirmed());
    }

    @Test
    void anActiveKeyIsSkippedForAnotherEntry() {
        market.active.add(IRON);
        MarketShockPayload.Config cfg = config(IRON, COPPER);
        for (int i = 0; i < 6; i++) {
            MarketShockPayload.State state = payload.createState(null, cfg);
            FakePayloadContext c = ctx();
            assertTrue(payload.onStart(c, cfg, state).started());
            assertEquals(COPPER, state.key());
            payload.onCleanup(c, cfg, state);
        }
        market.active.add(COPPER);
        assertEquals("shock_active", payload.onStart(ctx(), cfg, payload.createState(null, cfg)).skipReason());
    }

    @Test
    void invalidKeysAreIgnored() {
        MarketShockPayload.Config cfg = config("not a key", COPPER);
        assertFalse(payload.validate(cfg).isEmpty());
        MarketShockPayload.State state = payload.createState(null, cfg);
        assertTrue(payload.onStart(ctx(), cfg, state).started());
        assertEquals(COPPER, state.key());
    }

    @Test
    void resumePastTheEndResolvesEnded() {
        MarketShockPayload.Config cfg = config(IRON);
        MarketShockPayload.State saved = payload.createState(null, cfg);
        FakePayloadContext c = ctx();
        payload.onStart(c, cfg, saved);
        MarketShockPayload.State state = payload.loadState(null, cfg, payload.saveState(saved));
        market.applied.clear();
        wall[0] = state.endEpochMs() + 1;
        assertTrue(payload.onResume(c, cfg, state).resumed());
        assertTrue(market.applied.isEmpty());
        assertTrue(resolvedWith(payload.tick(c, cfg, state), EventOutcome.ENDED));
    }

    @Test
    void resumeBeforeTheEndReRequestsTheRemainingMinutes() {
        MarketShockPayload.Config cfg = config(IRON);
        MarketShockPayload.State saved = payload.createState(null, cfg);
        FakePayloadContext c = ctx();
        payload.onStart(c, cfg, saved);
        MarketShockPayload.State state = payload.loadState(null, cfg, payload.saveState(saved));
        market.applied.clear();
        market.minutes.clear();
        wall[0] = state.endEpochMs() - 90_000L; // 1.5 min left ⇒ ceil = 2
        assertTrue(payload.onResume(c, cfg, state).resumed());
        assertEquals(List.of(IRON), market.applied);
        assertEquals(List.of(2L), market.minutes);
        assertTrue(state.confirmed());
    }

    @Test
    void unconfirmedReRequestEndsEarlyWithoutCountingTowardThePause() {
        MarketShockPayload.Config cfg = config(IRON);
        market.confirm = false;
        assertTrue(resolvedWith(startAndWait(cfg, payload.createState(null, cfg), ctx()), EventOutcome.FAILURE));
        market.confirm = true;
        MarketShockPayload.State saved = payload.createState(null, cfg);
        payload.onStart(ctx(), cfg, saved);
        MarketShockPayload.State state = payload.loadState(null, cfg, payload.saveState(saved));
        market.confirm = false;
        FakePayloadContext c = ctx();
        wall[0] = state.endEpochMs() - 600_000L;
        payload.onResume(c, cfg, state);
        clock.advance(MarketShockPayload.CONFIRM_TICKS + 1);
        assertTrue(resolvedWith(payload.tick(c, cfg, state), EventOutcome.ENDED_EARLY));
        assertFalse(MarketShockPayload.isPaused());
    }

    @Test
    void cleanupEndsAConfirmedUnendedShock() {
        MarketShockPayload.Config cfg = config(IRON);
        MarketShockPayload.State state = payload.createState(null, cfg);
        FakePayloadContext c = ctx();
        payload.onStart(c, cfg, state);
        payload.onCleanup(c, cfg, state);
        assertEquals(List.of(IRON), market.ended);
        payload.onCleanup(c, cfg, state);
        assertEquals(List.of(IRON), market.ended, "cleanup is idempotent");
    }

    @Test
    void backstopEndsAfterTheGrace() {
        MarketShockPayload.Config cfg = config(IRON);
        MarketShockPayload.State state = payload.createState(null, cfg);
        FakePayloadContext c = ctx();
        payload.onStart(c, cfg, state);
        wall[0] = state.endEpochMs() + MarketShockPayload.END_GRACE_MS;
        clock.advance(1);
        assertTrue(resolvedWith(payload.tick(c, cfg, state), EventOutcome.ENDED));
    }
}
