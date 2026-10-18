package za.co.neroland.neroevents.reward;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.event.ContributionTable.Kind;
import za.co.neroland.neroevents.participation.ContributionListener;
import za.co.neroland.neroevents.participation.ContributionPolicy;
import za.co.neroland.neroevents.participation.WeightedContributionTable;

/** The integer split: deterministic, exact, floor-aware. */
class RewardSplitTest {

    private static final long SCALE = RewardSplit.SCALE;

    private static UUID id(int n) {
        return new UUID(0L, n);
    }

    /** A table where player {@code id(i)} dealt {@code damage[i]} damage, inserted in the given order. */
    private static List<RewardShare> ranked(double[] damage, boolean reversed) {
        WeightedContributionTable table = new WeightedContributionTable(ContributionPolicy.DEFAULT,
                ContributionListener.NONE);
        for (int k = 0; k < damage.length; k++) {
            int i = reversed ? damage.length - 1 - k : k;
            table.credit(id(i + 1), Kind.DAMAGE, damage[i]);
        }
        return table.ranked();
    }

    private static double[] spread(int n) {
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = 5.0D + (i * 7) % 23;
        }
        return out;
    }

    @Test
    void sameTableSameSharesAndAmountsInAnyInsertionOrder() {
        double[] damage = spread(7);
        List<RewardShare> a = RewardSplit.shares(ranked(damage, false), 0.05D, false);
        List<RewardShare> b = RewardSplit.shares(ranked(damage, false), 0.05D, false);
        List<RewardShare> c = RewardSplit.shares(ranked(damage, true), 0.05D, false);
        assertEquals(a, b);
        assertEquals(a, c);
        assertArrayEquals(RewardSplit.amounts(1234L, a), RewardSplit.amounts(1234L, c));
    }

    @Test
    void amountsSumToThePoolExactly() {
        for (int n : new int[] {1, 2, 5, 50}) {
            List<RewardShare> shares = RewardSplit.shares(ranked(spread(n), false), 0.05D, false);
            long total = 0L;
            for (RewardShare s : shares) {
                total += s.sharePpm();
            }
            assertEquals(SCALE, total, "shares sum to SCALE for n=" + n);
            for (long pool : new long[] {0L, 1L, 7L, 1000L, 999_983L}) {
                long sum = 0L;
                for (long a : RewardSplit.amounts(pool, shares)) {
                    sum += a;
                }
                assertEquals(pool, sum, "pool " + pool + " over " + n);
            }
        }
    }

    @Test
    void remainderUnitsGoToTheTopRanksInOrder() {
        List<RewardShare> shares = RewardSplit.shares(ranked(new double[] {4, 4, 4}, false), 0.0D, false);
        assertArrayEquals(new long[] {1L, 1L, 0L}, RewardSplit.amounts(2L, shares));
    }

    @Test
    void aTieOnWeightIsBrokenByUuidAscending() {
        List<RewardShare> shares = ranked(new double[] {6, 6}, true);
        assertEquals(id(1), shares.get(0).player());
        assertEquals(1, shares.get(0).rank());
        assertEquals(id(2), shares.get(1).player());
    }

    @Test
    void anEligibleSmallContributorGetsTheFloor() {
        List<RewardShare> ranked = ranked(new double[] {98, 2}, false);
        assertTrue(ranked.get(1).floorEligible(), "2 % raw ≥ the 1 % eligibility threshold");
        List<RewardShare> shares = RewardSplit.shares(ranked, 0.05D, false);
        long[] amounts = RewardSplit.amounts(1000L, shares);
        assertTrue(amounts[1] >= 1000L * 50_000L / SCALE - 1L, "≥ 5 % − 1 unit, got " + amounts[1]);
    }

    @Test
    void aContributorBelowTheEligibilityThresholdGetsNoFloor() {
        List<RewardShare> ranked = ranked(new double[] {199, 1}, false);
        assertFalse(ranked.get(1).floorEligible(), "0.5 % raw");
        List<RewardShare> shares = RewardSplit.shares(ranked, 0.05D, false);
        long rest = SCALE - 50_000L; // one eligible participant carries the floor
        long expectedShare = RewardSplit.mulDiv(rest, ranked.get(1).weightMilli(), 200_000L);
        assertEquals(expectedShare, shares.get(1).sharePpm());
        long[] amounts = RewardSplit.amounts(1000L, shares);
        long exact = RewardSplit.mulDiv(1000L, expectedShare, SCALE);
        assertTrue(amounts[1] == exact || amounts[1] == exact + 1L, "got " + amounts[1] + ", expected " + exact);
    }

    @Test
    void tooManyFloorsBecomeEqualShares() {
        double[] damage = new double[25];
        for (int i = 0; i < damage.length; i++) {
            damage[i] = 10 + i;
        }
        List<RewardShare> ranked = ranked(damage, false);
        ranked.forEach(r -> assertTrue(r.floorEligible()));
        for (RewardShare s : RewardSplit.shares(ranked, 0.05D, false)) {
            assertEquals(40_000L, s.sharePpm(), "25 × 5 % > 100 % ⇒ m = 1/25 for everyone");
        }
    }

    @Test
    void theFloorDivisionShortfallGoesToRankOne() {
        List<RewardShare> ranked = ranked(new double[] {3, 3, 3}, false);
        List<RewardShare> shares = RewardSplit.shares(ranked, 0.0D, false);
        assertEquals(333_334L, shares.get(0).sharePpm());
        assertEquals(333_333L, shares.get(1).sharePpm());
        assertEquals(333_333L, shares.get(2).sharePpm());
    }

    @Test
    void fourItemsOverFiveParticipants() {
        List<RewardShare> shares = RewardSplit.shares(ranked(new double[] {5, 5, 5, 5, 5}, false), 0.05D, false);
        assertArrayEquals(new long[] {1L, 1L, 1L, 1L, 0L}, RewardSplit.amounts(4L, shares));
    }

    @Test
    void equalSplitIgnoresWeight() {
        List<RewardShare> shares = RewardSplit.shares(ranked(new double[] {90, 10}, false), 0.0D, true);
        List<Long> ppm = new ArrayList<>();
        shares.forEach(s -> ppm.add(s.sharePpm()));
        assertEquals(List.of(500_000L, 500_000L), ppm);
    }
}
