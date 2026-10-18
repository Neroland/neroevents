package za.co.neroland.neroevents.event;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One instance's consent vote, in memory only. Counts are public; who voted which way never leaves this
 * object, is never written anywhere and is erased per player on request. Only online players vote (the
 * command is player-only).
 */
public class VoteTally {

    public enum CastResult { RECORDED, CHANGED, CLOSED }

    public enum Decision { PASSED, FAILED_QUORUM, FAILED_MAJORITY }

    private final Map<UUID, Boolean> votes = new HashMap<>();
    private boolean open;

    /** Opens the vote (the engine does this on entering VOTING). */
    public void openVoting() {
        open = true;
    }

    /** Casts or changes a vote while open; a re-vote replaces the earlier one. */
    public CastResult cast(UUID voter, boolean yes) {
        if (!open || voter == null) {
            return CastResult.CLOSED;
        }
        Boolean previous = votes.put(voter, yes);
        return previous == null ? CastResult.RECORDED : CastResult.CHANGED;
    }

    public void close() {
        open = false;
    }

    public boolean open() {
        return open;
    }

    public int yes() {
        int count = 0;
        for (boolean vote : votes.values()) {
            if (vote) {
                count++;
            }
        }
        return count;
    }

    public int no() {
        return votes.size() - yes();
    }

    /**
     * The decision: quorum is the larger of {@code quorumPlayers} and {@code ceil(quorumFraction × online)};
     * the vote passes when yes votes are strictly more than {@code majorityFraction} of votes cast, so a
     * tie fails.
     */
    public Decision decide(int online, int quorumPlayers, double quorumFraction, double majorityFraction) {
        int quorum = Math.max(quorumPlayers, (int) Math.ceil(quorumFraction * online));
        int yes = yes();
        int cast = yes + no();
        if (cast < quorum) {
            return Decision.FAILED_QUORUM;
        }
        return yes > majorityFraction * cast ? Decision.PASSED : Decision.FAILED_MAJORITY;
    }

    /** Whether {@code voter} has a vote in the tally. */
    public boolean hasVoted(UUID voter) {
        return voter != null && votes.containsKey(voter);
    }

    /** The requesting player's own choice (never anyone else's): yes = true. */
    public Optional<Boolean> choiceOf(UUID voter) {
        return voter == null ? Optional.empty() : Optional.ofNullable(votes.get(voter));
    }

    /** Drops the votes of players who are not in {@code online} (leaving counts as abstaining). */
    public void retain(Set<UUID> online) {
        votes.keySet().retainAll(online);
    }

    /** Erasure. */
    public void forget(UUID voter) {
        votes.remove(voter);
    }

    public void clear() {
        votes.clear();
    }
}
