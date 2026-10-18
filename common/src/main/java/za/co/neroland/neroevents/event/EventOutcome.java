package za.co.neroland.neroevents.event;

import java.util.Locale;

/**
 * How an instance that ran ended. {@code SUCCESS}/{@code FAILURE} come from the payload,
 * {@code EXPIRED} from the timer, {@code ESCAPED} for a boss nobody fought, {@code ENDED} /
 * {@code ENDED_EARLY} for timed market effects, {@code INTERRUPTED} for an instance that was mid-resolve
 * when the server stopped.
 */
public enum EventOutcome {
    SUCCESS, FAILURE, EXPIRED, ESCAPED, ENDED, ENDED_EARLY, INTERRUPTED;

    /** The lower-case spelling used in translation keys. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
