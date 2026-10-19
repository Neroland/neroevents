package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.RecordingPayloadType;

/** Cooldowns, caps, the calm window, consent modes and kill switches. */
class SchedulerCapsTest {

    private TestEngine t;
    private RecordingPayloadType type;

    @BeforeEach
    void setUp() {
        t = new TestEngine();
        type = new RecordingPayloadType("caps");
    }

    @AfterEach
    void tearDown() {
        t.close();
    }

    private String code(EventDefinition def) {
        return EventScheduler.eligibility(def, t.engine.snapshot(), t.settings).code();
    }

    @Test
    void cooldownBlocksUntilItsEnd() {
        EventDefinition def = t.define("cooldown", type, null);
        t.state.setCooldownUntil(def.id(), t.clock.now + 100);
        assertEquals("cooldown", code(def));
        t.clock.advance(100);
        assertNull(code(def));
    }

    @Test
    void skippedAndCancelledHalveTheCooldown() {
        EventDefinition def = t.define("halve", type, "{\"cooldown_ticks\": 1000}");
        EventInstance instance = t.start(def);
        assertEquals(t.clock.now + 1000, t.state.cooldownUntil(def.id()));
        t.engine.cancel(instance.id(), "admin");
        assertEquals(t.clock.now + 500, t.state.cooldownUntil(def.id()));

        type.start = StartResult.skip("no_room");
        EventInstance skipped = t.start(t.define("halve_skip", type, "{\"announce_ticks\": 0, \"cooldown_ticks\": 1000}"));
        t.tick(1);
        assertEquals(EventPhase.SKIPPED, skipped.phase());
        assertEquals(skipped.phaseSince() + 500, t.state.cooldownUntil(skipped.definition().id()));
    }

    @Test
    void bandCapCountsLiveInstances() {
        EventDefinition def = t.define("band", type, null);
        assertNull(code(def));
        t.start(t.define("busy", type, null));
        assertEquals("band_cap", code(def));
    }

    @Test
    void oneBossAtATimeAndOnePerDimension() {
        t.world.online(FakeWorldView.OVERWORLD, 3).online(FakeWorldView.NETHER, 3);
        EventDefinition boss = t.define("boss", type, "{\"category\": \"boss\"}");
        assertNull(code(boss));
        t.start(t.define("first_boss", type, "{\"category\": \"boss\"}"));
        assertEquals("type_cap", code(boss));

        t.settings.maxConcurrentBossEvents = 2;
        EventScheduler.Eligibility e = EventScheduler.eligibility(boss, t.engine.snapshot(), t.settings);
        assertTrue(e.eligible());
        assertEquals(List.of(FakeWorldView.NETHER), e.candidateDimensions());
    }

    @Test
    void perDimensionBossCapCanEmptyTheCandidates() {
        t.world.online(FakeWorldView.OVERWORLD, 6);
        t.settings.maxConcurrentBossEvents = 2;
        EventDefinition boss = t.define("lonely_boss", type,
                "{\"category\": \"boss\", \"conditions\": {\"dimensions\": [\"minecraft:overworld\"]}}");
        t.start(t.define("first_boss", type, "{\"category\": \"boss\"}"));
        assertEquals("boss_dimension_cap", code(boss));
    }

    @Test
    void invasionCapApplies() {
        t.world.online(FakeWorldView.OVERWORLD, 6);
        EventDefinition invasion = t.define("invasion", type, "{\"category\": \"invasion\"}");
        t.start(invasion);
        assertEquals("type_cap", code(t.define("invasion2", type, "{\"category\": \"invasion\"}")));
        assertNull(code(t.define("other", type, null)));
    }

    @Test
    void globalCooldownGatesTheNextRoll() {
        t.settings.schedulerBaseIntervalTicks = 1200L;
        t.settings.schedulerGlobalCooldownTicks = 50_000L;
        long next = EventScheduler.nextRollAt(1000L, t.settings, BandTable.DEFAULT.bandFor(3), new Random(1L));
        assertEquals(51_000L, next);
        t.settings.schedulerGlobalCooldownTicks = 0L;
        t.settings.schedulerJitterFraction = 0.0D;
        assertEquals(1000L + 1200L, EventScheduler.nextRollAt(1000L, t.settings, BandTable.DEFAULT.bandFor(3),
                new Random(1L)));
    }

    @Test
    void calmWindowBlocksOnlyNonExemptDefinitions() {
        t.settings.calmWindowHours = 1;
        assertEquals("calm_window", code(t.define("calm", type, null)));
        assertNull(code(t.define("calm_exempt", type, "{\"calm_exempt\": true}")));
        t.clock.now = 72_000L;
        assertNull(code(t.define("after_calm", type, null)));
    }

    @Test
    void firstEventHoldRuleOrderAndOrigins() {
        int calm = EventScheduler.RULES.indexOf("calm_window");
        assertEquals(calm + 1, EventScheduler.RULES.indexOf("first_event"));
        t.world.dedicated = false; // one player on an integrated server
        t.clock.now = 600L;
        EventDefinition exempt = t.define("hold_exempt", type, "{\"calm_exempt\": true}");
        SchedulerSnapshot snap = t.engine.snapshot();
        assertTrue(snap.singlePlayer());
        assertTrue(snap.firstEventHold());
        assertEquals("first_event", code(exempt));
        EventScheduler.RuleCheck rule = EventScheduler.explain(exempt, snap, t.settings).stream()
                .filter(c -> c.rule().equals("first_event")).findFirst().orElseThrow();
        assertEquals(false, rule.passed());
        // The Supply Beacon (REQUEST) and operators (ADMIN) are never held.
        assertInstanceOf(RequestResult.Queued.class,
                t.engine.request(new EventRequest(exempt.id(), FakeWorldView.OVERWORLD, EventOrigin.REQUEST, null)));
        t.engine.skipUpcoming("admin");
        assertInstanceOf(RequestResult.Queued.class,
                t.engine.request(new EventRequest(exempt.id(), FakeWorldView.OVERWORLD, EventOrigin.ADMIN, null)));
    }

    @Test
    void schedulerRollDuringTheHoldCreatesNothingAndKeepsTheRandom() {
        t.world.dedicated = false;
        t.settings.schedulerEnabled = true;
        t.define("held", type, "{\"calm_exempt\": true}");
        Random twin = new Random(7L);
        t.clock.now = 600L;
        t.engine.tickOnce();
        assertTrue(t.engine.live().isEmpty());
        assertTrue(t.engine.recent().isEmpty());
        assertEquals(t.settings.singlePlayerFirstEventTicks(), t.state.nextRollAt());
        assertEquals(twin.nextLong(), t.random.nextLong(), "the hold consumed no random numbers");
    }

    @Test
    void adminConsentNeverRolls() {
        t.settings.consentMode = ConsentMode.ADMIN;
        t.world.online(FakeWorldView.OVERWORLD, 3);
        EventDefinition def = t.define("admin_mode", type, null);
        assertEquals("consent_admin", code(def));
        t.settings.schedulerEnabled = true;
        t.clock.now = 600L;
        t.engine.tickOnce();
        assertTrue(t.engine.live().isEmpty());
        assertInstanceOf(RequestResult.Queued.class,
                t.engine.request(new EventRequest(def.id(), FakeWorldView.OVERWORLD, EventOrigin.ADMIN, null)));
    }

    @Test
    void integratedServerConsentRules() {
        assertEquals(ConsentMode.OFF, ConsentMode.effective(ConsentMode.VOTE, true, false, 1));
        assertEquals(ConsentMode.VOTE, ConsentMode.effective(ConsentMode.VOTE, true, false, 2));
        assertEquals(ConsentMode.VOTE, ConsentMode.effective(ConsentMode.VOTE, true, true, 1));
        assertEquals(ConsentMode.OFF, ConsentMode.effective(ConsentMode.VOTE, false, true, 5));
        assertEquals(ConsentMode.ADMIN, ConsentMode.effective(ConsentMode.ADMIN, false, true, 5));
        t.settings.consentMode = ConsentMode.VOTE;
        t.world.dedicated = false;
        assertEquals(ConsentMode.OFF, t.engine.effectiveConsent());
        t.world.online(FakeWorldView.OVERWORLD, 2);
        assertEquals(ConsentMode.VOTE, t.engine.effectiveConsent());
        t.settings.consentVotingEnabled = false;
        assertEquals(ConsentMode.OFF, t.engine.effectiveConsent());
    }

    @Test
    void killSwitchesRemoveByCategoryAndDestructive() {
        EventDefinition destructive = t.define("destructive", type, "{\"destructive\": true}");
        EventDefinition raid = t.define("raid", type, "{\"category\": \"raid\"}");
        EventDefinition economy = t.define("economy", type, "{\"category\": \"economy\"}");
        t.settings.destructiveEventsEnabled = false;
        t.settings.combatEventsEnabled = false;
        t.settings.economyEventsEnabled = false;
        assertEquals("kill_switch", code(destructive));
        assertEquals("kill_switch", code(raid));
        assertEquals("kill_switch", code(economy));
        RequestResult refused = t.engine.request(new EventRequest(destructive.id(), FakeWorldView.OVERWORLD,
                EventOrigin.ADMIN, null));
        assertEquals("kill_switch", ((RequestResult.Refused) refused).code());
    }

    @Test
    void weightZeroIsRequestOnly() {
        EventDefinition def = t.define("request_only", type, "{\"weight\": 0}");
        assertEquals("request_only", code(def));
        t.settings.schedulerEnabled = true;
        t.clock.now = 600L;
        t.engine.tickOnce();
        assertTrue(t.engine.live().isEmpty());
        assertInstanceOf(RequestResult.Queued.class,
                t.engine.request(new EventRequest(def.id(), FakeWorldView.OVERWORLD, EventOrigin.ADMIN, null)));
    }

    @Test
    void schedulerRollsAnEligibleDefinition() {
        t.settings.schedulerEnabled = true;
        EventDefinition def = t.define("rolled", type, null);
        t.clock.now = 600L;
        t.engine.tickOnce();
        assertEquals(1, t.engine.live().size());
        assertEquals(def, t.engine.live().get(0).definition());
        assertEquals(EventOrigin.SCHEDULER, t.engine.live().get(0).origin());
        assertTrue(t.state.nextRollAt() > 600L);
    }
}
