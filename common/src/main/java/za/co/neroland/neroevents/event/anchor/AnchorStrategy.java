package za.co.neroland.neroevents.event.anchor;

/** How an instance's anchor is chosen. */
public enum AnchorStrategy {
    /** No anchor: the event covers the whole dimension. */
    NONE,
    /** A density cell weighted by online-player counts, then a random protected-checked column in it. */
    DENSITY,
    /** The payload names the target; density fallback when it finds none (if the definition allows). */
    TARGET
}
