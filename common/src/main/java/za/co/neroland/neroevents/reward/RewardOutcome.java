package za.co.neroland.neroevents.reward;

import java.util.Map;
import java.util.UUID;

/**
 * What one instance's resolve paid, in memory only. The per-player map exists for the resolver's own
 * bookkeeping and the in-memory reward bus; history keeps only the totals.
 *
 * @param pool            the value units the lines were scaled to
 * @param factorPpm       the scale factor applied to every line, parts per million
 * @param perPlayer       what each participant actually got (in memory only)
 * @param chargedValue    value units charged to today's budget, the external boss charge included
 * @param currencyPaid    credits applied in total
 * @param xpGranted       experience granted in total (direct and crate)
 * @param itemStacks      reward stacks stored in crates in total
 * @param currencySkipped credits were due but NeroEconomy is not installed
 * @param currencyRefused currency payments NeroEconomy refused
 * @param duplicates      grants skipped because the participant was already paid for this instance
 * @param escaped         a boss event resolved as a success with nobody credited: the engine turns it into ESCAPED
 * @param budgetExhausted today's budget was spent; nothing was paid
 * @param participants    credited participants
 */
public record RewardOutcome(long pool, long factorPpm, Map<UUID, RewardSummary> perPlayer, long chargedValue,
        long currencyPaid, long xpGranted, int itemStacks, boolean currencySkipped, int currencyRefused,
        int duplicates, boolean escaped, boolean budgetExhausted, int participants) {

    public static final RewardOutcome NONE = none(0);

    public RewardOutcome {
        perPlayer = Map.copyOf(perPlayer);
    }

    public static RewardOutcome none(int participants) {
        return new RewardOutcome(0L, 0L, Map.of(), 0L, 0L, 0L, 0, false, 0, 0, false, false, participants);
    }

    public static RewardOutcome escapedOutcome() {
        return new RewardOutcome(0L, 0L, Map.of(), 0L, 0L, 0L, 0, false, 0, 0, true, false, 0);
    }

    /** Like {@link #none} but carrying a charge already made (the external boss payout). */
    public static RewardOutcome charged(int participants, long chargedValue, boolean budgetExhausted) {
        return new RewardOutcome(0L, 0L, Map.of(), chargedValue, 0L, 0L, 0, false, 0, 0, false, budgetExhausted,
                participants);
    }
}
