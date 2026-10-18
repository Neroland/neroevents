package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.neroevents.event.EventSignal.Kind;
import za.co.neroland.neroevents.progression.EventMilestones;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.RecordingPayloadType;

/** Every lifecycle path, on a plain JVM against fakes. */
class LifecycleTest {

    private static final List<ThresholdEvents.ThresholdCrossing> CROSSINGS = new ArrayList<>();

    static {
        ThresholdEvents.onCrossing(crossing -> {
            if (EventMilestones.EVENT_RESOLVED.equals(crossing.channel())) {
                CROSSINGS.add(crossing);
            }
        });
    }

    private TestEngine t;
    private RecordingPayloadType type;

    @BeforeEach
    void setUp() {
        CROSSINGS.clear();
        t = new TestEngine();
        type = new RecordingPayloadType("lifecycle");
    }

    @AfterEach
    void tearDown() {
        t.close();
    }

    @Test
    void happyPathRunsEveryPhaseInOrderAndFiresResolvedWithTheWorldCount() {
        type.script = n -> n >= 5 ? TickResult.resolved(EventOutcome.SUCCESS) : TickResult.proceed();
        EventDefinition def = t.define("happy", type, null);
        EventInstance first = t.start(def);
        assertEquals(EventPhase.SCHEDULED, first.phase());
        t.tick(1);
        assertEquals(EventPhase.ANNOUNCED, first.phase());
        t.tick(120);
        assertEquals(EventPhase.DONE, first.phase());
        assertEquals(EventOutcome.SUCCESS, first.outcome());
        assertEquals(List.of(Kind.SCHEDULED, Kind.ANNOUNCED, Kind.STARTED, Kind.RESOLVED, Kind.REWARDED, Kind.CLEANED,
                Kind.DONE), t.kinds());
        assertEquals(1L, type.count("onStart"));
        assertEquals(1L, type.count("onCleanup"));
        assertEquals(1, t.rewarded.size());
        assertEquals(1, CROSSINGS.size());
        assertEquals(1L, CROSSINGS.get(0).value());
        assertEquals(def.id().toString(), CROSSINGS.get(0).scope());

        EventInstance second = t.start(def);
        t.tick(130);
        assertEquals(EventPhase.DONE, second.phase());
        assertEquals(2, CROSSINGS.size());
        assertEquals(2L, CROSSINGS.get(1).value());
        assertTrue(t.engine.live().isEmpty());
    }

    @Test
    void zeroAnnounceStartsOnTheFirstTick() {
        EventDefinition def = t.define("instant", type, "{\"announce_ticks\": 0}");
        EventInstance instance = t.start(def);
        t.tick(1);
        assertEquals(EventPhase.ACTIVE, instance.phase());
    }

    @Test
    void failedQuorumSkipsHalvesCooldownAndNeverStarts() {
        t.settings.consentMode = ConsentMode.VOTE;
        t.world.online(FakeWorldView.OVERWORLD, 4);
        EventDefinition def = t.define("vote_quorum", type, null);
        EventInstance instance = scheduled(def);
        t.tick(1);
        assertEquals(EventPhase.VOTING, instance.phase());
        t.engine.vote(instance, UUID.randomUUID(), true);
        t.tick(110);
        assertEquals(EventPhase.SKIPPED, instance.phase());
        assertEquals("vote_failed", instance.reason());
        assertEquals(0L, type.count("onStart"));
        assertEquals(instance.phaseSince() + def.cooldownTicks() / 2, t.state.cooldownUntil(def.id()));
        assertTrue(t.announcer.lines.contains("skipped:vote_failed"));
    }

    @Test
    void majorityTieFailsAndAClearMajorityPasses() {
        t.settings.consentMode = ConsentMode.VOTE;
        t.world.online(FakeWorldView.OVERWORLD, 2);
        EventDefinition def = t.define("vote_tie", type, null);
        EventInstance tie = scheduled(def);
        t.tick(1);
        t.engine.vote(tie, UUID.randomUUID(), true);
        t.engine.vote(tie, UUID.randomUUID(), false);
        t.tick(110);
        assertEquals(EventPhase.SKIPPED, tie.phase());

        EventInstance pass = scheduled(t.define("vote_pass", type, null));
        t.tick(1);
        UUID a = UUID.randomUUID();
        t.engine.vote(pass, a, false);
        assertEquals(VoteTally.CastResult.CHANGED, t.engine.vote(pass, a, true));
        t.engine.vote(pass, UUID.randomUUID(), true);
        t.tick(110);
        assertEquals(EventPhase.ACTIVE, pass.phase());
    }

    @Test
    void startSkipCarriesThePayloadReason() {
        type.start = StartResult.skip("no_sky");
        EventInstance instance = t.start(t.define("no_sky", type, "{\"announce_ticks\": 0}"));
        t.tick(1);
        assertEquals(EventPhase.SKIPPED, instance.phase());
        assertEquals("no_sky", instance.reason());
    }

    @Test
    void timeoutUsesThePayloadsOutcome() {
        type.timeout = EventOutcome.FAILURE;
        EventInstance instance = t.start(t.define("timeout", type, "{\"announce_ticks\": 0, \"duration_ticks\": 20}"));
        t.tick(30);
        assertEquals(1L, type.count("onTimeout"));
        assertEquals(EventOutcome.FAILURE, instance.outcome());
        assertEquals(EventPhase.DONE, instance.phase());
    }

    @Test
    void throwingTickCancelsAndCleansUpOnce() {
        type.throwOnTick = true;
        EventInstance instance = t.start(t.define("throws", type, "{\"announce_ticks\": 0}"));
        t.tick(5);
        assertEquals(EventPhase.CANCELLED, instance.phase());
        assertEquals("payload_error", instance.reason());
        assertEquals(1L, type.count("onCleanup"));
        assertEquals(1L, type.count("tick"));
    }

    @Test
    void cancelFromActiveCleansUpOnceAndFiresNoResolved() {
        EventInstance instance = t.start(t.define("cancel", type, "{\"announce_ticks\": 0}"));
        t.tick(3);
        assertEquals(EventPhase.ACTIVE, instance.phase());
        assertTrue(t.engine.cancel(instance.id(), "admin"));
        assertFalse(t.engine.cancel(instance.id(), "admin"));
        t.tick(3);
        assertEquals(EventPhase.CANCELLED, instance.phase());
        assertEquals(1L, type.count("onCleanup"));
        assertTrue(CROSSINGS.isEmpty());
        assertFalse(t.kinds().contains(Kind.RESOLVED));
    }

    @Test
    void skipUpcomingLeavesActiveAlone() {
        EventInstance active = t.start(t.define("active", type, "{\"announce_ticks\": 0}"));
        t.tick(1);
        EventInstance upcoming = t.start(t.define("upcoming", type, null));
        t.tick(1);
        assertEquals(1, t.engine.skipUpcoming("admin"));
        assertEquals(EventPhase.ACTIVE, active.phase());
        assertEquals(EventPhase.CANCELLED, upcoming.phase());
    }

    @Test
    void illegalTransitionThrows() {
        EventDefinition def = t.define("illegal", type, null);
        EventInstance instance = new EventInstance(UUID.randomUUID(), def, FakeWorldView.OVERWORLD, null,
                Footprint.dimension(FakeWorldView.OVERWORLD), EventOrigin.ADMIN, 1L, 0L, EventPhase.SCHEDULED);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> instance.transition(EventPhase.REWARDED, 1L));
        assertTrue(e.getMessage().contains("illegal event phase transition SCHEDULED → REWARDED"));
    }

    @Test
    void terminalInstancesAreEvictedAfterTheRetainWindow() {
        t.settings.instanceRetainTicks = 50;
        EventInstance instance = t.start(t.define("evict", type, "{\"announce_ticks\": 0}"));
        t.tick(2);
        t.engine.cancel(instance.id(), "admin");
        t.tick(1);
        assertEquals(1, t.engine.recent().size());
        t.tick(60);
        assertTrue(t.engine.recent().isEmpty());
    }

    @Test
    void adminOriginNeverVotes() {
        t.settings.consentMode = ConsentMode.VOTE;
        t.world.online(FakeWorldView.OVERWORLD, 5);
        EventInstance instance = t.start(t.define("admin_no_vote", type, null));
        t.tick(1);
        assertEquals(EventPhase.ANNOUNCED, instance.phase());
        t.tick(110);
        assertEquals(EventPhase.ACTIVE, instance.phase());
    }

    @Test
    void erasureForgetsVotesAndContributions() {
        t.settings.consentMode = ConsentMode.VOTE;
        t.world.online(FakeWorldView.OVERWORLD, 3);
        EventInstance instance = scheduled(t.define("erase", type, null));
        t.tick(1);
        UUID player = UUID.randomUUID();
        t.engine.vote(instance, player, true);
        instance.contributions().credit(player, ContributionTable.Kind.OBJECTIVE, 3.0D);
        assertEquals(1, instance.votes().yes());
        assertEquals(1, instance.contributions().participantCount());
        t.engine.forget(player);
        assertEquals(0, instance.votes().yes());
        assertEquals(0, instance.contributions().participantCount());
        assertEquals(0.0D, instance.contributions().share(player));
    }

    /** An instance as the scheduler would create it (the only origin that votes). */
    private EventInstance scheduled(EventDefinition def) {
        t.settings.schedulerEnabled = true;
        t.state.setNextRollAt(0L);
        t.defs.keySet().removeIf(id -> !id.equals(def.id()));
        t.defs.put(def.id(), def);
        long now = t.clock.now;
        long nextPass = ((now / t.settings.schedulerIntervalTicks) + 1) * t.settings.schedulerIntervalTicks;
        t.clock.now = nextPass;
        t.engine.tickOnce();
        t.clock.advance(1);
        t.settings.schedulerEnabled = false;
        return t.engine.live().stream().filter(i -> i.definition() == def).findFirst()
                .orElseThrow(() -> new AssertionError("scheduler did not roll " + def.id()));
    }
}
