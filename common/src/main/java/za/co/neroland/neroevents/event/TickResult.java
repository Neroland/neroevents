package za.co.neroland.neroevents.event;

/** What a payload's {@code tick} reports: keep going, resolved with an outcome, or progress made. */
public sealed interface TickResult {

    TickResult CONTINUE = new Continue();

    static TickResult proceed() {
        return CONTINUE;
    }

    static TickResult resolved(EventOutcome outcome) {
        return new Resolved(outcome);
    }

    static TickResult progress(int done, int total) {
        return new Progress(done, total);
    }

    /** Nothing to report; the engine's timer still applies. */
    record Continue() implements TickResult {
    }

    /** The event is over with this outcome. */
    record Resolved(EventOutcome outcome) implements TickResult {
    }

    /** Still running; progress for the HUD and the companion app (coalesced by the engine). */
    record Progress(int done, int total) implements TickResult {
    }
}
