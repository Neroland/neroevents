package za.co.neroland.neroevents.vote;

import java.util.Set;
import java.util.UUID;

import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.event.VoteTally;

/**
 * The consent rules on top of the in-memory tally: quorum is the larger of {@code voteQuorumPlayers} and
 * {@code ceil(voteQuorumFraction × online)}, yes must be a strict majority (a tie fails), votes of players
 * who have left are dropped at decision time (leaving = abstaining), and the vote closes early once every
 * online player has voted. The event still starts at its countdown's end. Only numbers ever leave the
 * instance.
 */
public record VotePolicy(int quorumPlayers, double quorumFraction, double majorityFraction, boolean earlyClose) {

    /** From the engine's settings snapshot (which mirrors the config keys). */
    public static VotePolicy fromSettings(EngineSettings settings) {
        return new VotePolicy(settings.voteQuorumPlayers, settings.voteQuorumFraction, settings.voteMajorityFraction,
                settings.voteEarlyCloseEnabled);
    }

    /** From the live config. */
    public static VotePolicy fromConfig() {
        return fromSettings(EngineSettings.fromConfig());
    }

    /** Drops leavers, then decides over the online count. */
    public VoteTally.Decision decide(VoteTally tally, Set<UUID> online) {
        tally.retain(online);
        return tally.decide(online.size(), quorumPlayers, quorumFraction, majorityFraction);
    }

    /** Whether voting can end now: enabled, still open, and every online player has voted. */
    public boolean shouldCloseEarly(VoteTally tally, Set<UUID> online) {
        return earlyClose && tally.open() && !online.isEmpty() && online.stream().allMatch(tally::hasVoted);
    }
}
