package za.co.neroland.neroevents.event.reward;

import java.util.UUID;

import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.reward.RewardOutcome;

/**
 * Pays an instance's participants when it resolves. The engine calls {@link #resolve} exactly once, on
 * RESOLVING → REWARDED. {@link #NONE} pays nothing (tests); the budgeted resolver replaces it on a running
 * server without an engine change.
 */
public interface RewardResolver {

    RewardOutcome resolve(EventInstance instance);

    /** Today's remaining reward budget in value units (the scheduler's boss pre-check reads it). */
    default long remainingDailyBudget(long nowEpochMillis) {
        return Long.MAX_VALUE;
    }

    /** Erasure: drops anything in memory naming the player (session crate, notices). */
    default void forget(UUID player) {
    }

    /** Conformance probe for the resolver's in-memory state. */
    default boolean holds(UUID player) {
        return false;
    }

    /** Pays nothing. */
    RewardResolver NONE = instance -> RewardOutcome.NONE;
}
