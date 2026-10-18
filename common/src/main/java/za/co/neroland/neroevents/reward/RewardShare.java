package za.co.neroland.neroevents.reward;

import java.util.UUID;

/**
 * One credited participant's place in the split. In memory only, for the resolver; never shown,
 * broadcast or stored.
 *
 * @param player        the participant
 * @param rank          1-based rank (weight descending, UUID ascending)
 * @param weightMilli   {@code round(weight × 1000)}
 * @param sharePpm      share in parts per million (raw share from the table; final share after the split)
 * @param floorEligible whether the raw share reaches the floor-eligibility threshold
 */
public record RewardShare(UUID player, int rank, long weightMilli, long sharePpm, boolean floorEligible) {
}
