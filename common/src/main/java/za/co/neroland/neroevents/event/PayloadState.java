package za.co.neroland.neroevents.event;

/**
 * Marker for a payload type's per-instance mutable state. Saved through
 * {@code PayloadType.saveState} as part of the resumable record: never put a player's identity in it.
 */
public interface PayloadState {
}
