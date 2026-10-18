package za.co.neroland.neroevents.event;

/**
 * One lifecycle signal on the engine-wide bus. It carries the instance's public {@link EventView} and
 * nothing that names a player.
 */
public record EventSignal(Kind kind, EventView view, long at) {

    public enum Kind {
        SCHEDULED, ANNOUNCED, VOTING_OPENED, VOTE_CHANGED, STARTED, PROGRESS, RESOLVED, REWARDED, CANCELLED,
        SKIPPED, CLEANED, DONE,
        /** Every online player voted: voting ended early (the event still starts when the countdown ends). */
        VOTE_CLOSED
    }
}
