package za.co.neroland.neroevents.event;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.Identifier;

/**
 * One scheduler pass's frozen view of the world, built once per pass from the {@code WorldView} in
 * O(players + players × referenced gates). Every eligibility rule is an O(1) check against it.
 * {@code gateHolders} and {@code dimensionTags} hold only the gates and dimension-type tags the pool
 * actually references. {@code remainingDailyBudget} is {@code Long.MAX_VALUE} until rewards land.
 */
public record SchedulerSnapshot(long now, int online, PopulationBand band, boolean calmWindow,
        ConsentMode effectiveConsent, boolean dedicated, Map<Identifier, Integer> onlinePerDimension,
        List<Identifier> loadedDimensions, Map<Identifier, Long> dayTimePerDimension,
        Map<Identifier, Integer> gateHolders, Map<Identifier, Set<Identifier>> dimensionTags, Set<String> loadedMods,
        Map<EventCategory, Integer> activeByCategory, Map<Identifier, Integer> activeBossPerDimension,
        int activeTotal, long lastFiredAt, Map<Identifier, Long> cooldownUntil, Map<Identifier, Integer> reweights,
        long remainingDailyBudget) {

    /**
     * The participant count budgets are checked against: the band's upper bound, or for the open top
     * band the larger of the current online count and the band's floor.
     */
    public int expectedParticipants() {
        return band.maxOnline() < 0 ? Math.max(online, band.minOnline()) : band.maxOnline();
    }

    public int activeIn(EventCategory category) {
        return activeByCategory.getOrDefault(category, 0);
    }
}
