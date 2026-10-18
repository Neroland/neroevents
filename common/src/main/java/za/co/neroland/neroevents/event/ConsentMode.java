package za.co.neroland.neroevents.event;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * Who decides whether an automatic event happens. {@code OFF}: it just happens. {@code VOTE}: online
 * players vote during the countdown. {@code ADMIN}: the scheduler never rolls; only
 * {@code /neroevents start} (and a player's own request item) fire anything.
 */
public enum ConsentMode {
    OFF, VOTE, ADMIN;

    /** The config spelling. */
    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** {@code off|vote|admin}, case-insensitive; {@code null} when unreadable. */
    public static @Nullable ConsentMode parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "off" -> OFF;
            case "vote" -> VOTE;
            case "admin" -> ADMIN;
            default -> null;
        };
    }

    /**
     * The mode the scheduler actually applies: an integrated (non-dedicated) server with one player or
     * fewer is always {@code OFF}, and {@code VOTE} with voting switched off is {@code OFF}.
     */
    public static ConsentMode effective(ConsentMode configured, boolean votingEnabled, boolean dedicated,
            int online) {
        if (!dedicated && online <= 1) {
            return OFF;
        }
        if (configured == VOTE && !votingEnabled) {
            return OFF;
        }
        return configured;
    }
}
