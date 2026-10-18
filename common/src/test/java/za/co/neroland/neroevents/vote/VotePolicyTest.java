package za.co.neroland.neroevents.vote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.event.VoteTally;
import za.co.neroland.neroevents.event.VoteTally.Decision;

/** Quorum, majority, leavers, early close. */
class VotePolicyTest {

    private static final VotePolicy POLICY = new VotePolicy(2, 0.5D, 0.5D, true);

    private static Set<UUID> online(int n) {
        Set<UUID> out = new LinkedHashSet<>();
        for (int i = 1; i <= n; i++) {
            out.add(new UUID(0L, i));
        }
        return out;
    }

    private static VoteTally tally(List<UUID> yes, List<UUID> no) {
        VoteTally tally = new VoteTally();
        tally.openVoting();
        yes.forEach(v -> tally.cast(v, true));
        no.forEach(v -> tally.cast(v, false));
        return tally;
    }

    @Test
    void quorumIsTheLargerOfPlayersAndFraction() {
        // online 1: quorum max(2, 1) = 2 ⇒ one vote fails
        Set<UUID> one = online(1);
        assertEquals(Decision.FAILED_QUORUM, POLICY.decide(tally(List.copyOf(one), List.of()), one));
        // online 3: quorum max(2, 2) = 2
        List<UUID> three = List.copyOf(online(3));
        assertEquals(Decision.PASSED, POLICY.decide(tally(three.subList(0, 2), List.of()), online(3)));
        // online 4: quorum max(2, 2) = 2
        List<UUID> four = List.copyOf(online(4));
        assertEquals(Decision.PASSED, POLICY.decide(tally(four.subList(0, 2), List.of()), online(4)));
        // online 10: quorum max(2, 5) = 5
        List<UUID> ten = List.copyOf(online(10));
        assertEquals(Decision.FAILED_QUORUM, POLICY.decide(tally(ten.subList(0, 4), List.of()), online(10)));
        assertEquals(Decision.PASSED, POLICY.decide(tally(ten.subList(0, 5), List.of()), online(10)));
    }

    @Test
    void aTieFails() {
        List<UUID> four = List.copyOf(online(4));
        assertEquals(Decision.FAILED_MAJORITY, POLICY.decide(tally(four.subList(0, 2), four.subList(2, 4)), online(4)));
    }

    @Test
    void leaversAreDroppedAtDecisionTime() {
        List<UUID> voters = List.copyOf(online(3));
        VoteTally tally = tally(voters, List.of());
        Set<UUID> stillOnline = new LinkedHashSet<>(List.of(voters.get(0), new UUID(1L, 1L), new UUID(1L, 2L),
                new UUID(1L, 3L)));
        assertEquals(Decision.FAILED_QUORUM, POLICY.decide(tally, stillOnline), "3 yes, 2 left, online 4");
        assertEquals(1, tally.yes());
    }

    @Test
    void earlyCloseNeedsEveryOnlinePlayerAndTheSwitch() {
        Set<UUID> online = online(3);
        List<UUID> all = List.copyOf(online);
        VoteTally partial = tally(all.subList(0, 2), List.of());
        assertFalse(POLICY.shouldCloseEarly(partial, online));
        VoteTally complete = tally(all.subList(0, 2), all.subList(2, 3));
        assertTrue(POLICY.shouldCloseEarly(complete, online));
        assertFalse(new VotePolicy(2, 0.5D, 0.5D, false).shouldCloseEarly(complete, online));
        complete.close();
        assertFalse(POLICY.shouldCloseEarly(complete, online), "already closed");
    }

    @Test
    void aLateJoinerMayVote() {
        Set<UUID> online = online(2);
        VoteTally tally = tally(List.copyOf(online), List.of());
        UUID late = new UUID(2L, 2L);
        assertEquals(VoteTally.CastResult.RECORDED, tally.cast(late, true));
        online.add(late);
        assertEquals(Decision.PASSED, POLICY.decide(tally, online));
    }

    @Test
    void choiceOfIsForTheAskingPlayer() {
        UUID me = new UUID(0L, 1L);
        VoteTally tally = tally(List.of(me), List.of(new UUID(0L, 2L)));
        assertEquals(Optional.of(true), tally.choiceOf(me));
        assertEquals(Optional.empty(), tally.choiceOf(new UUID(9L, 9L)));
        assertTrue(tally.hasVoted(me));
    }
}
