package za.co.neroland.neroevents.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.event.EventSignal;
import za.co.neroland.neroevents.event.Severity;
import za.co.neroland.neroevents.payload.PayloadSounds;

/** Pins the effect rates and caps, the announcement cues and the sound range and pitch rules. */
class EffectsBudgetTest {

    private static final double NEAR = 10.0D * 10.0D;
    private static final double FAR = 97.0D * 97.0D;

    @Test
    void meteorTrailsRunAtFullRateUpToTwentyInView() {
        assertFalse(TrailBudget.halfRate(20, 1));
        assertEquals(2, TrailBudget.smoke(false, NEAR));
        assertEquals(3, TrailBudget.embers(false, NEAR, 7));
    }

    @Test
    void beyondTwentyInViewEverySecondMeteorHalves() {
        assertTrue(TrailBudget.halfRate(21, 1));
        assertFalse(TrailBudget.halfRate(21, 2), "even meteors keep full rate");
        assertEquals(1, TrailBudget.smoke(true, NEAR));
        // Half of three embers per tick: 2 and 1 on alternating ticks.
        assertEquals(3, TrailBudget.embers(true, NEAR, 0) + TrailBudget.embers(true, NEAR, 1));
    }

    @Test
    void trailsStopBeyondNinetySixBlocks() {
        assertEquals(0, TrailBudget.smoke(false, FAR));
        assertEquals(0, TrailBudget.embers(false, FAR, 0));
        assertEquals(0, TrailBudget.debrisSmoke(FAR));
        assertEquals(1, TrailBudget.debrisSmoke(NEAR));
    }

    @Test
    void impactFlashFollowsTheSwitchAndSkipsSoftLandings() {
        assertTrue(TrailBudget.impactFlash(false, true));
        assertFalse(TrailBudget.impactFlash(false, false), "the HUD switch turns the flash off");
        assertFalse(TrailBudget.impactFlash(true, true), "a soft landing has no flash");
        assertEquals(24, TrailBudget.impactEmbers(false));
        assertEquals(12, TrailBudget.impactEmbers(true));
    }

    @Test
    void podBeaconRingAndSparksKeepTheirCadence() {
        assertFalse(TrailBudget.beaconRingDue(0), "legs fold out first");
        assertTrue(TrailBudget.beaconRingDue(8));
        assertFalse(TrailBudget.beaconRingDue(9));
        assertTrue(TrailBudget.beaconRingDue(28));
        assertTrue(TrailBudget.sparkDue(true, 20));
        assertFalse(TrailBudget.sparkDue(true, 21));
        assertFalse(TrailBudget.sparkDue(false, 20), "only an open pod sparks");
        assertTrue(TrailBudget.podInRange(48.0D * 48.0D));
        assertFalse(TrailBudget.podInRange(49.0D * 49.0D));
    }

    @Test
    void announceCuesFollowTheSeverity() {
        assertEquals(List.of(new EventSounds.Cue("event.announce.info", 0),
                new EventSounds.Cue("event.announce.info_tail", 6)), EventSounds.announceCues(Severity.INFO));
        assertEquals(List.of(new EventSounds.Cue("event.announce.reward", 0)), EventSounds.announceCues(Severity.REWARD));
        assertEquals(List.of(new EventSounds.Cue("event.announce.hazard", 0),
                new EventSounds.Cue("event.announce.hazard_tail", 6)), EventSounds.announceCues(Severity.HAZARD));
        assertEquals(List.of(new EventSounds.Cue("event.announce.combat", 0)), EventSounds.announceCues(Severity.COMBAT));
        assertEquals(List.of(new EventSounds.Cue("event.announce.war", 0),
                new EventSounds.Cue("event.announce.war_layer", 0)), EventSounds.announceCues(Severity.WAR),
                "war's bell layers on the horn, not after it");
        assertTrue(EventSounds.announceCues(null).isEmpty());
    }

    @Test
    void onlyAnnounceStartAndVoteOpenMakeSound() {
        assertEquals(EventSounds.announceCues(Severity.HAZARD), EventSounds.cuesFor(EventSignal.Kind.ANNOUNCED,
                Severity.HAZARD));
        assertEquals(EventSounds.announceCues(Severity.REWARD), EventSounds.cuesFor(EventSignal.Kind.STARTED,
                Severity.REWARD));
        assertEquals(List.of(new EventSounds.Cue("event.vote.open", 0)),
                EventSounds.cuesFor(EventSignal.Kind.VOTING_OPENED, Severity.INFO));
        for (EventSignal.Kind kind : List.of(EventSignal.Kind.PROGRESS, EventSignal.Kind.VOTE_CHANGED,
                EventSignal.Kind.RESOLVED, EventSignal.Kind.CANCELLED, EventSignal.Kind.DONE)) {
            assertTrue(EventSounds.cuesFor(kind, Severity.WAR).isEmpty(), kind.name());
        }
    }

    @Test
    void soundRangesBecomeVolumesAndCapturePitchRises() {
        assertEquals(1.0F, PayloadSounds.volumeFor(16.0F));
        assertEquals(1.0F, PayloadSounds.volumeFor(8.0F), "never quieter than full");
        assertEquals(8.0F, PayloadSounds.volumeFor(128.0F));
        assertEquals(6.0F, PayloadSounds.volumeFor(96.0F));
        assertEquals(1.0F, PayloadSounds.captureTickPitch(0, 600));
        assertEquals(1.25F, PayloadSounds.captureTickPitch(300, 600), 1.0e-6F);
        assertEquals(1.5F, PayloadSounds.captureTickPitch(600, 600), 1.0e-6F);
        assertEquals(1.5F, PayloadSounds.captureTickPitch(900, 600), 1.0e-6F, "capped at the full capture");
        assertEquals(1.0F, PayloadSounds.captureTickPitch(10, 0));
    }
}
