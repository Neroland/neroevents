package za.co.neroland.neroevents.reward;

/**
 * The world's reward spend for one UTC calendar day of the server clock: one aggregate pair, no player
 * anywhere near it.
 */
public record DailyBudget(long epochDay, long spent) {

    private static final long DAY_MS = 86_400_000L;

    /** The UTC calendar day of {@code epochMillis}. */
    public static long epochDay(long epochMillis) {
        return Math.floorDiv(epochMillis, DAY_MS);
    }

    /** This budget, or a fresh one with nothing spent when {@code nowEpochMillis} is on a later day. */
    public DailyBudget rolled(long nowEpochMillis) {
        long today = epochDay(nowEpochMillis);
        return today == epochDay ? this : new DailyBudget(today, 0L);
    }

    /** What is left of {@code cap}; never negative. */
    public long remaining(long cap) {
        return Math.max(0L, cap - spent);
    }
}
