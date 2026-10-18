package za.co.neroland.neroevents.event;

import net.minecraft.resources.Identifier;

/**
 * One complaint from the last event-definition load: which definition, which JSON key, why, and whether
 * the whole definition was dropped or only the field reset to its default.
 *
 * <p><b>Privacy:</b> resource ids, key names and codec messages only — never player data, never a file
 * path (an exception's own message can carry one, so only its type is quoted).
 */
public record ValidationIssue(Identifier id, String key, String message, boolean dropped) {

    public static ValidationIssue dropped(Identifier id, String key, String message) {
        return new ValidationIssue(id, key, message, true);
    }

    public static ValidationIssue ignored(Identifier id, String key, String message) {
        return new ValidationIssue(id, key, message, false);
    }

    /** {@code [DROPPED] ns:id weight - must be 0 to 1000}. */
    public String describe() {
        return "[" + (dropped ? "DROPPED" : "IGNORED") + "] " + id + " " + key + " - " + message;
    }
}
