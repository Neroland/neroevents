package za.co.neroland.neroevents.event.reward;

import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventView;

/**
 * Receives the aggregate outcome of every instance that ran (resolved or interrupted by a restart), was
 * skipped or was cancelled. Aggregate only: nothing it stores names a player. {@link #NONE} keeps nothing
 * (tests); the persistent history replaces it on a running server.
 */
public interface EventHistorySink {

    void record(EventView aggregate);

    /** The engine's call: the instance in its terminal (or REWARDED) phase at engine time {@code now}. */
    default void record(EventInstance instance, long now) {
        record(instance.view(now));
    }

    /** Keeps nothing. */
    EventHistorySink NONE = aggregate -> {
    };
}
