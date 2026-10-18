package za.co.neroland.neroevents.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What one participant got from one instance, as stored in their own ledger row and shown only to them.
 *
 * @param currencyPaid     credits actually applied by NeroEconomy
 * @param xpGranted        experience given directly or put in the reward crate
 * @param itemStacks       reward stacks stored in the crate
 * @param itemCount        items in those stacks
 * @param reputationLines  reputation changes applied
 * @param currencySkipped  credits were due but this server has no NeroEconomy
 * @param rank             the participant's rank in the split
 * @param participantCount credited participants in the instance
 * @param sharePpm         the participant's final share, parts per million
 */
public record RewardSummary(long currencyPaid, long xpGranted, int itemStacks, int itemCount, int reputationLines,
        boolean currencySkipped, int rank, int participantCount, long sharePpm) {

    public static final Codec<RewardSummary> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.LONG.optionalFieldOf("currency_paid", 0L).forGetter(RewardSummary::currencyPaid),
            Codec.LONG.optionalFieldOf("xp_granted", 0L).forGetter(RewardSummary::xpGranted),
            Codec.INT.optionalFieldOf("item_stacks", 0).forGetter(RewardSummary::itemStacks),
            Codec.INT.optionalFieldOf("item_count", 0).forGetter(RewardSummary::itemCount),
            Codec.INT.optionalFieldOf("reputation_lines", 0).forGetter(RewardSummary::reputationLines),
            Codec.BOOL.optionalFieldOf("currency_skipped", false).forGetter(RewardSummary::currencySkipped),
            Codec.INT.optionalFieldOf("rank", 0).forGetter(RewardSummary::rank),
            Codec.INT.optionalFieldOf("participant_count", 0).forGetter(RewardSummary::participantCount),
            Codec.LONG.optionalFieldOf("share_ppm", 0L).forGetter(RewardSummary::sharePpm)
    ).apply(inst, RewardSummary::new));

    /**
     * A copy with {@code rank} and {@code sharePpm} zeroed, for an anonymous ledger row (an opted-out
     * player): with the other rows named, a rank or a share would point at the one person left over.
     * {@code participantCount} is kept; it describes the instance, not the player.
     */
    public RewardSummary anonymised() {
        return new RewardSummary(currencyPaid, xpGranted, itemStacks, itemCount, reputationLines, currencySkipped, 0,
                participantCount, 0L);
    }
}
