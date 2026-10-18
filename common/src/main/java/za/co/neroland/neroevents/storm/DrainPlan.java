package za.co.neroland.neroevents.storm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * Pure: which storages one storm drain pass touches and how much it takes from each.
 *
 * <pre>
 * fraction = stormDrainFractionPerPass × intensity / 3 × variant.drainMultiplier()
 * for each candidate that is exposed, allowed by protection and holds energy:
 *     floor   = round(capacity × stormDrainFloorFraction)
 *     extract = min(round(amount × fraction), stormDrainMaxPerPass, max(0, amount − floor))
 * </pre>
 *
 * A storage is never drained below its floor, never by more than the per-pass cap, and never at all when
 * it is sheltered or protected (deny wins). A variant that does not drain plans nothing.
 */
public final class DrainPlan {

    /** One storage the storm reaches. {@code allowed} is the protection verdict for its position. */
    public record Candidate(BlockPos pos, long amount, long capacity, boolean exposed, boolean allowed) {
    }

    /** Take {@code extract} NE from the storage at {@code pos}. */
    public record Drain(BlockPos pos, long extract) {
    }

    /** The settings one pass reads (a copy of the engine's storm keys). */
    public record Limits(double fractionPerPass, long maxPerPass, double floorFraction, int maxStoragesPerPass) {
    }

    private DrainPlan() {
    }

    /** The drain fraction for one pass. */
    public static double fraction(StormVariant variant, int intensity, Limits limits) {
        if (!variant.drains()) {
            return 0.0D;
        }
        return limits.fractionPerPass() * StormVariant.clampIntensity(intensity) / 3.0D * variant.drainMultiplier();
    }

    /** The plan for one pass over {@code candidates} (already capped by {@link #cap}). */
    public static List<Drain> of(List<Candidate> candidates, StormVariant variant, int intensity, Limits limits) {
        double fraction = fraction(variant, intensity, limits);
        List<Drain> out = new ArrayList<>();
        if (fraction <= 0.0D || limits.maxPerPass() <= 0L) {
            return out;
        }
        double floorFraction = Math.max(0.0D, Math.min(1.0D, limits.floorFraction()));
        for (Candidate c : candidates) {
            if (!c.exposed() || !c.allowed() || c.amount() <= 0L) {
                continue;
            }
            long floor = Math.round(Math.max(0L, c.capacity()) * floorFraction);
            long headroom = Math.max(0L, c.amount() - floor);
            long extract = Math.min(Math.min(Math.round(c.amount() * fraction), limits.maxPerPass()), headroom);
            if (extract > 0L) {
                out.add(new Drain(c.pos(), extract));
            }
        }
        return out;
    }

    /** Keeps the first {@code max} entries in order (fairness across passes comes from players moving). */
    public static <T> List<T> cap(List<T> candidates, int max) {
        if (candidates.size() <= Math.max(0, max)) {
            return candidates;
        }
        return new ArrayList<>(candidates.subList(0, Math.max(0, max)));
    }
}
