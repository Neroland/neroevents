package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.RecordingPayloadType;

/**
 * Single-player pacing on the shipped supply drop and meteor shower: the first-event hold, the short calm
 * window, the solo interval and the shortened cooldowns, and that none of it applies with two players or with
 * {@code singlePlayerPacing=false}.
 */
class SinglePlayerPacingTest {

    private static final long HOLD = 8 * 1200L;
    private static final Identifier SUPPLY = Identifier.fromNamespaceAndPath("neroevents", "core/supply_drop");

    private TestEngine t;
    private EventDefinition supply;
    private EventDefinition meteor;

    @BeforeEach
    void setUp() {
        t = fixture(1, true);
    }

    @AfterEach
    void tearDown() {
        t.close();
    }

    /** An integrated server with {@code online} players in the overworld, real calm window, scheduler on. */
    private TestEngine fixture(int online, boolean pacing) {
        TestEngine e = new TestEngine();
        e.world.dedicated = false;
        e.world.online(FakeWorldView.OVERWORLD, online).playerAt(FakeWorldView.OVERWORLD, 100, 100);
        e.settings.schedulerEnabled = true;
        e.settings.calmWindowHours = 48;
        e.settings.singlePlayerPacing = pacing;
        RecordingPayloadType type = new RecordingPayloadType("pacing");
        supply = e.defineShipped("core/supply_drop", type);
        meteor = e.defineShipped("core/meteor_shower", type);
        e.clock.now = 0L;
        return e;
    }

    /** One scheduler-aligned tick at {@code now}. */
    private static void passAt(TestEngine e, long now) {
        e.clock.now = now;
        e.engine.tickOnce();
    }

    private static List<EventInstance> created(TestEngine e) {
        List<EventInstance> out = new ArrayList<>(e.engine.live());
        out.addAll(e.engine.recent());
        return out;
    }

    @Test
    void firstEventWaitsForTheHoldThenOnlyTheCalmExemptDropRolls() {
        passAt(t, 0L);
        assertTrue(created(t).isEmpty());
        assertEquals(HOLD, t.state.nextRollAt());
        for (long now = 600L; now < HOLD; now += 600L) {
            passAt(t, now);
            assertTrue(created(t).isEmpty(), "nothing before the hold ends (" + now + ")");
        }
        passAt(t, HOLD);
        List<EventInstance> made = created(t);
        assertEquals(1, made.size());
        assertEquals(EventPhase.SCHEDULED, made.get(0).phase());
        assertEquals(SUPPLY, made.get(0).definition().id());
        // Cooldown halved (72000 × 0.5), next roll at the solo interval (36000 × 1.0, ±50 %).
        assertEquals(HOLD + 36_000L, t.state.cooldownUntil(supply.id()));
        assertTrue(t.state.nextRollAt() >= HOLD + 18_000L && t.state.nextRollAt() <= HOLD + 54_000L,
                "next roll " + t.state.nextRollAt());
        // Never on the player: the anchor keeps the stand-off.
        var anchor = made.get(0).anchor();
        assertTrue(Math.max(Math.abs(anchor.getX() - 100), Math.abs(anchor.getZ() - 100)) > 24, anchor.toString());
    }

    @Test
    void theSoloCalmWindowIsThirtyMinutes() {
        t.settings.schedulerEnabled = false;
        t.clock.now = 20_000L;
        assertEquals("calm_window", EventScheduler.eligibility(meteor, t.engine.snapshot(), t.settings).code());
        t.clock.now = 36_000L;
        assertNull(EventScheduler.eligibility(meteor, t.engine.snapshot(), t.settings).code());
        assertEquals(36_000L, t.settings.calmWindowTicks(true));
    }

    @Test
    void twoPlayersGetServerPacing() {
        t.close();
        t = fixture(2, true);
        t.settings.schedulerJitterFraction = 0.0D;
        SchedulerSnapshot snap = t.engine.snapshot();
        assertFalse(snap.singlePlayer());
        assertFalse(snap.firstEventHold());
        assertEquals(48 * 72_000L, t.settings.calmWindowTicks(snap.singlePlayer()));
        assertTrue(snap.calmWindow());
        passAt(t, 0L);
        assertEquals(1, created(t).size(), "no first-event hold with two players");
        assertEquals(72_000L, t.state.cooldownUntil(supply.id()));
        // Band 2-4 (x1.0), not the solo multiplier: with no jitter exactly base × 1.0.
        assertEquals(1.0D, t.engine.bands().bandFor(2).intervalMultiplier());
        assertEquals(36_000L, t.state.nextRollAt());
    }

    @Test
    void pacingOffKeepsServerPacingAlone() {
        t.close();
        t = fixture(1, false);
        t.settings.schedulerJitterFraction = 0.0D;
        SchedulerSnapshot snap = t.engine.snapshot();
        assertTrue(snap.singlePlayer());
        assertFalse(snap.firstEventHold());
        assertEquals(48 * 72_000L, t.settings.calmWindowTicks(snap.singlePlayer()));
        passAt(t, 0L);
        assertEquals(1, created(t).size());
        assertEquals(72_000L, t.state.cooldownUntil(supply.id()));
        // Band 0-1 (x2.0).
        assertEquals(72_000L, t.state.nextRollAt());
        // And the solo multiplier is used only when pacing is on.
        t.settings.singlePlayerPacing = true;
        assertEquals(36_000L, EventScheduler.nextRollAt(0L, t.settings, t.engine.bands().bandFor(1), true,
                new java.util.Random(1L)));
        assertEquals(72_000L, EventScheduler.nextRollAt(0L, t.settings, t.engine.bands().bandFor(1), false,
                new java.util.Random(1L)));
    }

    @Test
    void theHoldNeverTouchesTheRandomSequence() {
        // Run A: the 8-minute hold. Run B: no hold. The first roll differs only in time.
        passAt(t, 0L);
        passAt(t, HOLD);
        EventInstance a = created(t).get(0);
        long aNext = t.state.nextRollAt() - HOLD;
        long aCooldown = t.state.cooldownUntil(supply.id()) - HOLD;

        TestEngine b = fixture(1, true);
        try {
            b.settings.singlePlayerFirstEventMinutes = 0;
            passAt(b, 0L);
            EventInstance first = created(b).get(0);
            assertEquals(a.definition().id(), first.definition().id());
            assertEquals(a.seed(), first.seed());
            assertEquals(a.anchor(), first.anchor());
            assertEquals(aNext, b.state.nextRollAt());
            assertEquals(aCooldown, b.state.cooldownUntil(supply.id()));
        } finally {
            b.close();
        }
    }

    @Test
    void settingsDefaultsMatchTheConfig() {
        EngineSettings s = EngineSettings.defaults();
        assertTrue(s.singlePlayerPacing);
        assertEquals(8, s.singlePlayerFirstEventMinutes);
        assertEquals(30, s.singlePlayerCalmWindowMinutes);
        assertEquals(1.0D, s.singlePlayerIntervalMultiplier);
        assertEquals(0.5D, s.singlePlayerCooldownMultiplier);
        assertEquals(24, s.anchorMinPlayerDistanceBlocks);
        assertEquals(HOLD, s.singlePlayerFirstEventTicks());
    }
}
