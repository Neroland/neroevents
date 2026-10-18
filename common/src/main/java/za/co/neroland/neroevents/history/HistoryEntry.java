package za.co.neroland.neroevents.history;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EventCategory;
import za.co.neroland.neroevents.event.EventOrigin;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.EventPhase;

/**
 * One event that happened (or did not), as everyone may see it: counts and totals only. No anchor, no
 * coordinates, no participant list, no top contributor; the vote numbers are the public ones that were
 * already broadcast live.
 *
 * @param startedAt epoch ms the instance went ACTIVE (its creation time when it never started)
 * @param endedAt   epoch ms it ended
 * @param terminal  DONE, SKIPPED or CANCELLED
 * @param rewardsTotalValue value units actually paid, the external boss charge included
 */
public record HistoryEntry(UUID instanceId, Identifier definitionId, Identifier type, EventCategory category,
        Identifier dimension, EventOrigin origin, long startedAt, long endedAt, EventPhase terminal,
        Optional<EventOutcome> outcome, Optional<String> reason, int participantCount, long rewardsTotalValue,
        long currencyPaid, long xpGranted, int itemStacks, int votesYes, int votesNo) {

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(name -> {
            try {
                return DataResult.success(Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "unknown " + type.getSimpleName() + " " + name);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }

    private record Identity(UUID instanceId, Identifier definitionId, Identifier type, EventCategory category,
            Identifier dimension, EventOrigin origin) {
    }

    private record Timing(long startedAt, long endedAt, EventPhase terminal, Optional<EventOutcome> outcome,
            Optional<String> reason) {
    }

    private record Totals(int participantCount, long rewardsTotalValue, long currencyPaid, long xpGranted,
            int itemStacks, int votesYes, int votesNo) {
    }

    private static final MapCodec<Identity> IDENTITY = RecordCodecBuilder.mapCodec(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf("instance").forGetter(Identity::instanceId),
            Identifier.CODEC.fieldOf("definition").forGetter(Identity::definitionId),
            Identifier.CODEC.fieldOf("type").forGetter(Identity::type),
            enumCodec(EventCategory.class).fieldOf("category").forGetter(Identity::category),
            Identifier.CODEC.fieldOf("dimension").forGetter(Identity::dimension),
            enumCodec(EventOrigin.class).fieldOf("origin").forGetter(Identity::origin)
    ).apply(inst, Identity::new));

    private static final MapCodec<Timing> TIMING = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf("started_at").forGetter(Timing::startedAt),
            Codec.LONG.fieldOf("ended_at").forGetter(Timing::endedAt),
            enumCodec(EventPhase.class).fieldOf("terminal").forGetter(Timing::terminal),
            enumCodec(EventOutcome.class).optionalFieldOf("outcome").forGetter(Timing::outcome),
            Codec.STRING.optionalFieldOf("reason").forGetter(Timing::reason)
    ).apply(inst, Timing::new));

    private static final MapCodec<Totals> TOTALS = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.optionalFieldOf("participants", 0).forGetter(Totals::participantCount),
            Codec.LONG.optionalFieldOf("rewards_value", 0L).forGetter(Totals::rewardsTotalValue),
            Codec.LONG.optionalFieldOf("currency_paid", 0L).forGetter(Totals::currencyPaid),
            Codec.LONG.optionalFieldOf("xp_granted", 0L).forGetter(Totals::xpGranted),
            Codec.INT.optionalFieldOf("item_stacks", 0).forGetter(Totals::itemStacks),
            Codec.INT.optionalFieldOf("votes_yes", 0).forGetter(Totals::votesYes),
            Codec.INT.optionalFieldOf("votes_no", 0).forGetter(Totals::votesNo)
    ).apply(inst, Totals::new));

    public static final Codec<HistoryEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            IDENTITY.forGetter(e -> new Identity(e.instanceId, e.definitionId, e.type, e.category, e.dimension,
                    e.origin)),
            TIMING.forGetter(e -> new Timing(e.startedAt, e.endedAt, e.terminal, e.outcome, e.reason)),
            TOTALS.forGetter(e -> new Totals(e.participantCount, e.rewardsTotalValue, e.currencyPaid, e.xpGranted,
                    e.itemStacks, e.votesYes, e.votesNo))
    ).apply(inst, (id, time, totals) -> new HistoryEntry(id.instanceId(), id.definitionId(), id.type(),
            id.category(), id.dimension(), id.origin(), time.startedAt(), time.endedAt(), time.terminal(),
            time.outcome(), time.reason(), totals.participantCount(), totals.rewardsTotalValue(),
            totals.currencyPaid(), totals.xpGranted(), totals.itemStacks(), totals.votesYes(), totals.votesNo())));
}
