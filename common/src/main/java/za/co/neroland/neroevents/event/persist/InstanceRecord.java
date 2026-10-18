package za.co.neroland.neroevents.event.persist;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EventOrigin;
import za.co.neroland.neroevents.event.EventPhase;

/**
 * The minimal resumable record of one live instance — enough to put a boss, a pod or a storm back in
 * the same phase with the same timers after {@code /stop}. World data only: the id is a random UUID the
 * engine minted, and {@code participantCount} is an aggregate. Votes and contributions are deliberately
 * absent.
 */
public record InstanceRecord(UUID id, Identifier definitionId, Identifier payloadType, Identifier dimension,
        Optional<BlockPos> anchor, EventOrigin origin, long seed, long createdAt, long startsAt, long endsAt,
        EventPhase phase, long phaseSince, int participantCount, CompoundTag payloadState) {

    public static final Codec<EventPhase> PHASE_CODEC = enumCodec(EventPhase.class);
    public static final Codec<EventOrigin> ORIGIN_CODEC = enumCodec(EventOrigin.class);

    public static final Codec<InstanceRecord> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(InstanceRecord::id),
            Identifier.CODEC.fieldOf("definition").forGetter(InstanceRecord::definitionId),
            Identifier.CODEC.fieldOf("payload_type").forGetter(InstanceRecord::payloadType),
            Identifier.CODEC.fieldOf("dimension").forGetter(InstanceRecord::dimension),
            BlockPos.CODEC.optionalFieldOf("anchor").forGetter(InstanceRecord::anchor),
            ORIGIN_CODEC.fieldOf("origin").forGetter(InstanceRecord::origin),
            Codec.LONG.fieldOf("seed").forGetter(InstanceRecord::seed),
            Codec.LONG.fieldOf("created_at").forGetter(InstanceRecord::createdAt),
            Codec.LONG.fieldOf("starts_at").forGetter(InstanceRecord::startsAt),
            Codec.LONG.fieldOf("ends_at").forGetter(InstanceRecord::endsAt),
            PHASE_CODEC.fieldOf("phase").forGetter(InstanceRecord::phase),
            Codec.LONG.fieldOf("phase_since").forGetter(InstanceRecord::phaseSince),
            Codec.INT.optionalFieldOf("participant_count", 0).forGetter(InstanceRecord::participantCount),
            CompoundTag.CODEC.optionalFieldOf("payload_state", new CompoundTag()).forGetter(InstanceRecord::payloadState)
    ).apply(inst, InstanceRecord::new));

    static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(name -> {
            try {
                return DataResult.success(Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "unknown " + type.getSimpleName() + " " + name);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
