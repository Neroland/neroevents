package za.co.neroland.neroevents.participation;

import java.util.UUID;

/** Told when a player is credited for the first time in one instance (the {@code first_participation} hook). */
@FunctionalInterface
public interface ContributionListener {

    void onFirstCredit(UUID player);

    /** Hears nothing. */
    ContributionListener NONE = player -> {
    };
}
