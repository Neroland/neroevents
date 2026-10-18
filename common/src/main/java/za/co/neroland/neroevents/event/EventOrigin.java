package za.co.neroland.neroevents.event;

/** Where an instance came from. Only {@link #SCHEDULER} instances ever vote. */
public enum EventOrigin {
    /** The automatic scheduler roll. */
    SCHEDULER,
    /** An operator's {@code /neroevents start}. */
    ADMIN,
    /** A gameplay request (a player's own request item), always consent-exempt. */
    REQUEST
}
