package za.co.neroland.neroevents.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.console.ConsoleIntents.Deny;
import za.co.neroland.neroevents.console.ConsoleIntents.PrivacyAction;
import za.co.neroland.neroevents.event.ConsentMode;
import za.co.neroland.neroevents.event.EventPhase;

/** The server-side validation of every console request, without a server. */
class ConsoleIntentsTest {

    @Test
    void votesNeedAnOpenTallyInVoteMode() {
        assertEquals(Optional.empty(), ConsoleIntents.checkVote(true, EventPhase.VOTING, true, false, ConsentMode.VOTE));
        assertEquals(Optional.of(Deny.VOTE_CLOSED),
                ConsoleIntents.checkVote(true, EventPhase.ANNOUNCED, false, false, ConsentMode.VOTE));
        assertEquals(Optional.of(Deny.VOTE_CLOSED),
                ConsoleIntents.checkVote(true, EventPhase.VOTING, false, false, ConsentMode.VOTE));
        assertEquals(Optional.of(Deny.VOTE_CLOSED),
                ConsoleIntents.checkVote(true, EventPhase.ACTIVE, true, false, ConsentMode.VOTE));
        assertEquals(Optional.of(Deny.VOTING_OFF),
                ConsoleIntents.checkVote(true, EventPhase.VOTING, true, false, ConsentMode.OFF));
        assertEquals(Optional.of(Deny.VOTING_OFF),
                ConsoleIntents.checkVote(true, EventPhase.VOTING, true, false, ConsentMode.ADMIN));
    }

    @Test
    void unknownOrCancelledEventsAreRefusedWithTheRightSentence() {
        assertEquals(Optional.of(Deny.VOTE_CLOSED), ConsoleIntents.checkVote(false, null, false, false, ConsentMode.VOTE));
        assertEquals(Optional.of(Deny.CANCELLED_BY_ADMIN),
                ConsoleIntents.checkVote(false, null, false, true, ConsentMode.VOTE));
        assertEquals(Optional.of(Deny.CANCELLED_BY_ADMIN),
                ConsoleIntents.checkVote(true, EventPhase.CANCELLED, false, false, ConsentMode.VOTE));
    }

    @Test
    void claimsExplainWhyNot() {
        assertEquals(Optional.of(Deny.NOTHING_TO_CLAIM), ConsoleIntents.checkClaim(0, 0L, true, false));
        assertEquals(Optional.of(Deny.ALREADY_CLAIMED), ConsoleIntents.checkClaim(0, 0L, true, true));
        assertEquals(Optional.of(Deny.MAKE_ROOM), ConsoleIntents.checkClaim(3, 0L, false, false));
        assertEquals(Optional.empty(), ConsoleIntents.checkClaim(3, 0L, true, false));
        // Experience needs no inventory room.
        assertEquals(Optional.empty(), ConsoleIntents.checkClaim(3, 20L, false, false));
        assertEquals(Optional.empty(), ConsoleIntents.checkClaim(0, 20L, false, false));
    }

    @Test
    void operatorControlsNeedThePermission() {
        assertEquals(Optional.of(Deny.NEEDS_OPERATOR), ConsoleIntents.checkOperator(false));
        assertEquals(Optional.empty(), ConsoleIntents.checkOperator(true));
    }

    @Test
    void privacyBytesOutOfRangeAreIgnored() {
        assertEquals(Optional.of(PrivacyAction.EXPORT), PrivacyAction.fromWire((byte) 0));
        assertEquals(Optional.of(PrivacyAction.OPT_IN), PrivacyAction.fromWire((byte) 2));
        assertEquals(Optional.empty(), PrivacyAction.fromWire((byte) 3));
        assertEquals(Optional.empty(), PrivacyAction.fromWire((byte) -1));
        assertEquals(Optional.empty(), PrivacyAction.fromWire(Byte.MAX_VALUE));
        for (PrivacyAction action : PrivacyAction.values()) {
            assertEquals(Optional.of(action), PrivacyAction.fromWire(action.wire()));
        }
    }

    @Test
    void theRateLimitHoldsPerSitting() {
        assertTrue(ConsoleIntents.allowed(Long.MIN_VALUE, 100L));
        assertFalse(ConsoleIntents.allowed(100L, 105L));
        assertTrue(ConsoleIntents.allowed(100L, 100L + ConsoleIntents.MIN_INTERVAL_TICKS));
        assertTrue(ConsoleIntents.allowed(100L, 50L), "a clock that went backwards resets");
    }

    @Test
    void aFloodOfRefusedRequestsEarnsOneTooFastAnswer() {
        ConsoleIntents.RateLimit limit = new ConsoleIntents.RateLimit();
        assertTrue(limit.tryRequest(100L), "the first request is answered");
        int denies = 0;
        for (int i = 0; i < 10; i++) {
            assertFalse(limit.tryRequest(101L));
            if (limit.tryDeny(101L)) {
                denies++;
            }
        }
        assertEquals(1, denies, "ten refused requests in one tick produce one deny");
        assertFalse(limit.tryRequest(105L));
        assertFalse(limit.tryDeny(105L), "still inside the deny interval: silent");
        assertTrue(limit.tryDeny(101L + ConsoleIntents.MIN_INTERVAL_TICKS), "a later flood is told once again");
        assertTrue(limit.tryRequest(100L + ConsoleIntents.MIN_INTERVAL_TICKS));
    }

    @Test
    void denyWireValuesRoundTripAndZeroMeansAllowed() {
        assertNull(Deny.fromWire((byte) 0));
        assertNull(Deny.fromWire((byte) 99));
        Set<String> keys = new HashSet<>();
        for (Deny deny : Deny.values()) {
            assertEquals(deny, Deny.fromWire(deny.wire()));
            assertTrue(keys.add(deny.key()), "duplicate key " + deny.key());
            assertTrue(deny.key().startsWith("console.neroevents.deny."));
        }
    }
}
