package za.co.neroland.neroevents.reward;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

/**
 * One ledger row: who (or nobody, for an opted-out player), which instance, what they got, when. An
 * anonymous row has no row id or slot and {@code at} is the instance's resolution time, identical for every
 * participant of that instance, so it cannot be linked back to anyone.
 */
public record RewardLedgerEntry(Optional<UUID> player, UUID instanceId, Identifier definitionId,
        RewardSummary summary, long at) {

    public static final Codec<RewardLedgerEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.optionalFieldOf("player").forGetter(RewardLedgerEntry::player),
            UUIDUtil.CODEC.fieldOf("instance").forGetter(RewardLedgerEntry::instanceId),
            Identifier.CODEC.fieldOf("definition").forGetter(RewardLedgerEntry::definitionId),
            RewardSummary.CODEC.fieldOf("summary").forGetter(RewardLedgerEntry::summary),
            Codec.LONG.fieldOf("at").forGetter(RewardLedgerEntry::at)
    ).apply(inst, RewardLedgerEntry::new));

    public boolean anonymous() {
        return player.isEmpty();
    }

    public boolean isFor(UUID subject) {
        return subject != null && player.isPresent() && player.get().equals(subject);
    }

    public RewardLedgerEntry withSummary(RewardSummary newSummary) {
        return new RewardLedgerEntry(player, instanceId, definitionId, newSummary, at);
    }
}
