package za.co.neroland.neroevents.event;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.protect.Footprint;

/**
 * The code behind one kind of event ({@code neroevents:supply_drop}, {@code neroevents:meteor_shower},
 * ...). Definitions name a type with {@code "type"}; the type decodes the definition's {@code payload}
 * object and drives the instance while it is active.
 *
 * <p>Rules every payload author follows:
 * <ul>
 *   <li><b>Server thread only.</b> The engine calls every method from its tick or from a command.</li>
 *   <li><b>Protection is not optional.</b> Any block or entity effect goes through
 *       {@link PayloadContext#protection()} first. A payload that places or breaks a block without asking
 *       fails review.</li>
 *   <li><b>Amortise your own tick.</b> {@link #tick} runs every tick; spread work with counters in the
 *       state so no single tick does more than it must. The engine measures, it does not throttle.</li>
 *   <li><b>Entities carry the instance id.</b> Anything spawned writes {@code NeroEventsInstance: <uuid>}
 *       into its persistent data so {@link #onCleanup} and a post-restart sweep can find it.</li>
 *   <li><b>No player identity in {@link #saveState}.</b> Entity UUIDs, fight ids, wave ids, keys and timers
 *       are fine; player UUIDs and names are not.</li>
 *   <li><b>Contribution only through {@link PayloadContext#contributions()}</b>, from attributable actions
 *       (damage dealt by, container opened by), never from who happens to be near.</li>
 * </ul>
 *
 * @param <C> the parsed {@code payload} config
 * @param <S> the per-instance mutable state
 */
public interface PayloadType<C extends PayloadConfig, S extends PayloadState> {

    /** The type id, e.g. {@code neroevents:supply_drop}. */
    Identifier id();

    /** Decodes a definition's {@code payload}; {@code MapCodec.unit(...)} for config-less types. */
    MapCodec<C> configCodec();

    /** Extra load-time warnings (each becomes an IGNORED validation row). */
    default List<String> validate(C config) {
        return List.of();
    }

    /** Scheduler-time exclusion (budget pre-checks, sibling availability). Empty = eligible. Must be O(1). */
    default Optional<String> preflight(SchedulerSnapshot snapshot, EventDefinition definition) {
        return Optional.empty();
    }

    /**
     * {@code bounds.kind = target}: where this event should happen; empty falls back or skips. The returned
     * position is still checked against {@code footprint()} by the protection query; a denied target counts
     * as empty.
     */
    default Optional<BlockPos> pickTarget(PayloadContext ctx, C config) {
        return Optional.empty();
    }

    /** The volume the anchor picker must clear with the protection query before accepting an anchor. */
    Footprint footprint(EventDefinition definition, C config, Identifier dimension, BlockPos anchor);

    /** Fresh state at scheduling; no world access. */
    S createState(EventInstance instance, C config);

    /** The countdown ended: start, or skip with a reason. */
    StartResult onStart(PayloadContext ctx, C config, S state);

    /** Every tick while active. */
    TickResult tick(PayloadContext ctx, C config, S state);

    /** The duration ran out. */
    default EventOutcome onTimeout(PayloadContext ctx, C config, S state) {
        return EventOutcome.EXPIRED;
    }

    /** Remove what the event left behind. Runs at cleanup and after a cancel; must be idempotent. */
    void onCleanup(PayloadContext ctx, C config, S state);

    /** The resumable part of the state. Cheap (counters and UUIDs); never player data. */
    CompoundTag saveState(S state);

    /** Restores {@link #saveState}'s output; may throw, which cancels the instance ({@code resume_failed}). */
    S loadState(EventInstance instance, C config, CompoundTag tag);

    /** After a restart into an active instance: carry on or refuse. */
    ResumeResult onResume(PayloadContext ctx, C config, S state);
}
