package za.co.neroland.neroevents.event;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The phases of one event instance and the legal transitions between them.
 *
 * <pre>
 * SCHEDULED -&gt; ANNOUNCED -&gt; (VOTING) -&gt; ACTIVE -&gt; RESOLVING -&gt; REWARDED -&gt; CLEANUP -&gt; DONE
 *                    \-&gt; SKIPPED (never became active)      \-&gt; CANCELLED (called off)
 * </pre>
 *
 * {@link #DONE}, {@link #SKIPPED} and {@link #CANCELLED} are terminal. Any transition missing from the
 * table is a bug and {@link EventInstance#transition} throws.
 */
public enum EventPhase {
    SCHEDULED, ANNOUNCED, VOTING, ACTIVE, RESOLVING, REWARDED, CLEANUP, DONE, SKIPPED, CANCELLED;

    private static final Map<EventPhase, Set<EventPhase>> LEGAL = new EnumMap<>(EventPhase.class);

    static {
        LEGAL.put(SCHEDULED, EnumSet.of(ANNOUNCED, SKIPPED, CANCELLED));
        LEGAL.put(ANNOUNCED, EnumSet.of(VOTING, ACTIVE, SKIPPED, CANCELLED));
        LEGAL.put(VOTING, EnumSet.of(ACTIVE, SKIPPED, CANCELLED));
        LEGAL.put(ACTIVE, EnumSet.of(RESOLVING, CANCELLED));
        // RESOLVING -> CLEANUP is the restart path: an instance stopped mid-resolve is cleaned up
        // without rewards.
        LEGAL.put(RESOLVING, EnumSet.of(REWARDED, CLEANUP));
        LEGAL.put(REWARDED, EnumSet.of(CLEANUP));
        LEGAL.put(CLEANUP, EnumSet.of(DONE));
        LEGAL.put(DONE, EnumSet.noneOf(EventPhase.class));
        LEGAL.put(SKIPPED, EnumSet.noneOf(EventPhase.class));
        LEGAL.put(CANCELLED, EnumSet.noneOf(EventPhase.class));
    }

    /** Whether this phase ends the instance. */
    public boolean terminal() {
        return this == DONE || this == SKIPPED || this == CANCELLED;
    }

    /** Whether the instance is still running (not terminal). */
    public boolean live() {
        return !terminal();
    }

    /** Whether the instance has not started yet (it can still be skipped). */
    public boolean upcoming() {
        return this == SCHEDULED || this == ANNOUNCED || this == VOTING;
    }

    /** Whether {@code from -> to} is a legal transition. */
    public static boolean canTransition(EventPhase from, EventPhase to) {
        return LEGAL.get(from).contains(to);
    }
}
