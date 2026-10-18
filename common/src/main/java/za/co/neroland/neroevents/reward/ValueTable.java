package za.co.neroland.neroevents.reward;

import java.util.List;

/**
 * Value units (VU), the one scale every reward budget is counted in: 1 credit = 1 VU, an XP point is
 * {@code xpValuePerPoint} VU, an item is its line's {@code value} or {@code itemDefaultValue} VU, and a
 * reputation change is 0 VU (it is bounded by its own per-event cap instead).
 */
public record ValueTable(double xpValuePerPoint, long itemDefaultValue) {

    /** The shipped defaults (0.25 VU per XP point, 10 VU per item). */
    public static final ValueTable DEFAULT = new ValueTable(0.25D, 10L);

    public long xpValue(long xp) {
        return Math.round(xp * xpValuePerPoint);
    }

    public long itemValue(RewardLine.Item line) {
        return line.value().orElse(itemDefaultValue);
    }

    public long valueOf(RewardLine line) {
        if (line instanceof RewardLine.Currency c) {
            return c.amount();
        }
        if (line instanceof RewardLine.Xp x) {
            return xpValue(x.amount());
        }
        if (line instanceof RewardLine.Item i) {
            return i.count() * itemValue(i);
        }
        return 0L; // reputation
    }

    /** The total value of {@code lines} (main and bonus). */
    public long total(List<? extends RewardLine> lines) {
        long sum = 0L;
        for (RewardLine line : lines) {
            sum += valueOf(line);
        }
        return sum;
    }
}
