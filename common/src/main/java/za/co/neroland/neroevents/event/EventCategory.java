package za.co.neroland.neroevents.event;

import java.util.Locale;
import java.util.Optional;

/**
 * What kind of event a definition is. Drives the kill switches ({@code combatEventsEnabled},
 * {@code economyEventsEnabled}), the per-type concurrency caps and the default {@link Severity}.
 */
public enum EventCategory {
    REWARD(Severity.REWARD),
    HAZARD(Severity.HAZARD),
    COMBAT(Severity.COMBAT),
    RAID(Severity.COMBAT),
    INVASION(Severity.COMBAT),
    BOSS(Severity.COMBAT),
    ECONOMY(Severity.INFO),
    WAR(Severity.WAR);

    private final Severity defaultSeverity;

    EventCategory(Severity defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
    }

    public Severity defaultSeverity() {
        return defaultSeverity;
    }

    /** The JSON spelling ({@code reward}, {@code boss}, ...). */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether {@code combatEventsEnabled=false} removes this category. */
    public boolean isCombat() {
        return this == COMBAT || this == RAID || this == INVASION || this == BOSS || this == WAR;
    }

    public static Optional<EventCategory> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (EventCategory category : values()) {
            if (category.serializedName().equals(name.trim().toLowerCase(Locale.ROOT))) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
