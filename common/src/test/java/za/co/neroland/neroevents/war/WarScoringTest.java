package za.co.neroland.neroevents.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.neroevents.event.ContributionTable;
import za.co.neroland.neroevents.payload.PendingCredits;
import za.co.neroland.neroevents.war.WarSides.Side;

/** Capture, contest, decay, hold, credit split, win score, ties, reputation lines and war_resolved. */
class WarScoringTest {

    private static final Identifier RED = WarSidesTest.RED;
    private static final Identifier BLUE = WarSidesTest.BLUE;

    private final Map<UUID, Set<Identifier>> members = new HashMap<>();
    private final WarSides sides = new WarSides(RED, BLUE);
    private final PendingCredits pending = new PendingCredits();

    @AfterEach
    void tearDown() {
        WarChannel.useForTests(null);
    }

    private UUID member(Identifier... factions) {
        UUID id = UUID.randomUUID();
        members.put(id, Set.of(factions));
        return id;
    }

    private static WarScoring scoring(int captureTicks, int hold, int win) {
        return new WarScoring(new WarScoring.Rules(captureTicks, 10, hold, win),
                List.of(new WarScoring.Objective(0, new BlockPos(0, 64, 0))));
    }

    private List<WarScoring.Capture> pass(WarScoring s, UUID... present) {
        return s.pass(List.of(List.of(present)), sides, p -> members.getOrDefault(p, Set.of()), pending, 0L);
    }

    @Test
    void captureAfterCaptureTicksUncontested() {
        WarScoring s = scoring(200, 0, 0);
        UUID red = member(RED);
        for (int i = 0; i < 9; i++) {
            assertTrue(pass(s, red).isEmpty());
        }
        assertEquals(180, s.objectives().get(0).progress());
        List<WarScoring.Capture> captures = pass(s, red);
        assertEquals(1, captures.size());
        assertEquals(Side.A, s.objectives().get(0).owner());
        assertEquals(0, s.objectives().get(0).progress());
        assertEquals(10, s.score(Side.A));
    }

    @Test
    void contestedFreezesProgress() {
        WarScoring s = scoring(200, 0, 0);
        UUID red = member(RED);
        UUID blue = member(BLUE);
        pass(s, red);
        pass(s, red);
        assertEquals(40, s.objectives().get(0).progress());
        pass(s, red, blue);
        assertTrue(s.objectives().get(0).contested());
        assertEquals(40, s.objectives().get(0).progress());
    }

    @Test
    void emptyObjectiveDecaysTenPerPass() {
        WarScoring s = scoring(200, 0, 0);
        UUID red = member(RED);
        pass(s, red);
        pass(s, red); // 40
        pass(s);
        assertEquals(30, s.objectives().get(0).progress());
        pass(s);
        pass(s);
        pass(s);
        pass(s);
        assertEquals(0, s.objectives().get(0).progress());
    }

    @Test
    void holdPointsAccrueInWholePoints() {
        WarScoring s = scoring(200, 1, 0);
        UUID red = member(RED);
        for (int i = 0; i < 10; i++) {
            pass(s, red);
        }
        assertEquals(10, s.score(Side.A)); // the capture
        for (int i = 0; i < 4; i++) {
            pass(s, red);
        }
        assertEquals(10, s.score(Side.A)); // 4 × 0.2 = 0.8
        pass(s, red);
        assertEquals(11, s.score(Side.A)); // 5 × 0.2 = 1 whole point
        assertEquals(0.0D, s.holdFraction(Side.A), 1e-9);
    }

    @Test
    void captureCreditIsSplitAmongCapturers() {
        WarScoring s = scoring(200, 0, 0);
        UUID r1 = member(RED);
        UUID r2 = member(RED);
        UUID neutral = member(RED, BLUE);
        for (int i = 0; i < 10; i++) {
            pass(s, r1, r2, neutral);
        }
        List<PendingCredits.Pending> credits = pending.snapshot();
        assertEquals(2, credits.size());
        for (PendingCredits.Pending credit : credits) {
            assertEquals(ContributionTable.Kind.OBJECTIVE, credit.kind());
            assertEquals(0.5D, credit.amount(), 1e-9);
            assertTrue(credit.player().equals(r1) || credit.player().equals(r2));
        }
    }

    @Test
    void neutralsNeverScoreOrCapture() {
        WarScoring s = scoring(200, 1, 0);
        UUID neutral = member(RED, BLUE);
        UUID nobody = member();
        for (int i = 0; i < 30; i++) {
            pass(s, neutral, nobody);
        }
        assertNull(s.objectives().get(0).owner());
        assertEquals(0, s.score(Side.A));
        assertEquals(0, s.score(Side.B));
        assertTrue(pending.snapshot().isEmpty());
    }

    @Test
    void winScoreEndsEarly() {
        WarScoring s = scoring(200, 0, 10);
        assertFalse(s.winReached());
        UUID blue = member(BLUE);
        for (int i = 0; i < 10; i++) {
            pass(s, blue);
        }
        assertTrue(s.winReached());
        assertEquals(Optional.of(Side.B), s.victor());
        assertFalse(scoring(200, 0, 0).winReached(), "win_score 0 never ends early");
    }

    @Test
    void tieHasNoVictor() {
        WarScoring s = scoring(200, 0, 0);
        assertEquals(Optional.empty(), s.victor());
        assertEquals(0, s.ownedByLeader());
    }

    @Test
    void reputationLinesAreCappedAndSkipNeutrals() {
        UUID red = member(RED);
        UUID blue = member(BLUE);
        UUID neutral = member(RED, BLUE);
        UUID unresolved = member(RED);
        sides.sideOf(red, p -> members.get(p));
        sides.sideOf(blue, p -> members.get(p));
        sides.sideOf(neutral, p -> members.get(p));
        List<WarScoring.ReputationLine> lines = WarScoring.reputationLines(
                List.of(red, blue, neutral, unresolved), sides, Side.A, 40, 30, 25);
        assertEquals(2, lines.size());
        Map<UUID, WarScoring.ReputationLine> by = new HashMap<>();
        lines.forEach(l -> by.put(l.player(), l));
        assertEquals(new WarScoring.ReputationLine(red, RED, 25), by.get(red));
        assertEquals(new WarScoring.ReputationLine(blue, BLUE, -25), by.get(blue));
        assertEquals(List.of(), WarScoring.reputationLines(List.of(red), sides, Side.NEUTRAL, 10, 5, 25));
    }

    @Test
    void warResolvedIsRisingForTheVictorOnly() {
        List<ThresholdEvents.ThresholdCrossing> fired = new ArrayList<>();
        WarChannel.useForTests(fired::add);
        WarChannel.resolved(RED, 30, true);
        WarChannel.resolved(BLUE, 12, false);
        assertEquals(2, fired.size());
        assertEquals(WarChannel.CHANNEL, fired.get(0).channel());
        assertEquals("nerofactions:red", fired.get(0).scope());
        assertEquals(30L, fired.get(0).value());
        assertEquals(1L, fired.get(0).threshold());
        assertTrue(fired.get(0).rising());
        assertFalse(fired.get(1).rising());
    }

    @Test
    void factionColoursAreDeterministic() {
        assertEquals(WarColours.of(RED), WarColours.of(Identifier.fromNamespaceAndPath("nerofactions", "red")));
        assertEquals(WarColours.UNOWNED, WarColours.of(null));
        assertEquals(0xFF0000, WarColours.hsbToRgb(0.0F, 1.0F, 1.0F));
    }
}
