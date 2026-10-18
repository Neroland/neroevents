package za.co.neroland.neroevents.event;

import java.util.Locale;
import java.util.Optional;

/** How an event presents itself (announcement styling arrives with the HUD). */
public enum Severity {
    INFO, REWARD, HAZARD, COMBAT, WAR;

    public static Optional<Severity> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
