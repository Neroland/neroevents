package za.co.neroland.neroevents.reward;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * The reward split, in integer arithmetic only, so it is reproducible from the contribution table alone:
 * shares in parts per million ({@link #SCALE}), a minimum-share floor that only floor-eligible
 * participants receive, floor division per line and the remainder handed out one unit each in rank order.
 * Pure; plain-JVM tested.
 */
public final class RewardSplit {

    public static final long SCALE = 1_000_000L;

    private RewardSplit() {
    }

    /** {@code a × b / c} without overflow (floor division; {@code c > 0}). */
    public static long mulDiv(long a, long b, long c) {
        if (c <= 0L) {
            return 0L;
        }
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        if ((high == 0L && low >= 0L) || (high == -1L && low < 0L)) {
            return Math.floorDiv(low, c);
        }
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(c)).longValue();
    }

    /**
     * The final shares. {@code ranked} must be in rank order (as {@link
     * za.co.neroland.neroevents.event.ContributionTable#ranked()} returns it). With {@code equal} every
     * participant weighs the same (the {@code split: equal} rule); ranks are unchanged.
     */
    public static List<RewardShare> shares(List<RewardShare> ranked, double minShareFraction, boolean equal) {
        int n = ranked.size();
        if (n == 0) {
            return List.of();
        }
        long m = Math.round(Math.max(0.0D, minShareFraction) * SCALE);
        long eligible = 0L;
        long total = 0L;
        for (RewardShare share : ranked) {
            if (share.floorEligible()) {
                eligible++;
            }
            total += equal ? 1L : share.weightMilli();
        }
        if (eligible > 0L && eligible * m > SCALE) {
            m = SCALE / eligible;
        }
        long floorMass = eligible * m;
        long rest = SCALE - floorMass;
        long[] s = new long[n];
        long sum = 0L;
        for (int i = 0; i < n; i++) {
            RewardShare share = ranked.get(i);
            long weight = equal ? 1L : share.weightMilli();
            s[i] = (share.floorEligible() ? m : 0L) + (total <= 0L ? 0L : mulDiv(rest, weight, total));
            sum += s[i];
        }
        s[0] += SCALE - sum; // the shortfall from floor division goes to rank 1
        List<RewardShare> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            RewardShare share = ranked.get(i);
            out.add(new RewardShare(share.player(), share.rank(), share.weightMilli(), s[i], share.floorEligible()));
        }
        return List.copyOf(out);
    }

    /**
     * Splits {@code pool} units over {@code shares} (rank order): {@code a_i = pool × s_i / SCALE}, then the
     * remainder one unit each to ranks 1..rem. The result sums to {@code pool} exactly.
     */
    public static long[] amounts(long pool, List<RewardShare> shares) {
        int n = shares.size();
        long[] a = new long[n];
        if (n == 0 || pool <= 0L) {
            return a;
        }
        long sum = 0L;
        for (int i = 0; i < n; i++) {
            a[i] = mulDiv(pool, shares.get(i).sharePpm(), SCALE);
            sum += a[i];
        }
        long rem = pool - sum;
        for (int k = 0; k < rem && k < n; k++) {
            a[k] += 1L;
        }
        return a;
    }
}
