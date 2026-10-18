package za.co.neroland.neroevents.event;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.protect.Footprint;

/**
 * {@code neroevents:debug_marker} — a payload that does nothing in the world. It resolves
 * {@code SUCCESS} after {@code ticks_to_resolve} active ticks (default: the definition's duration) and
 * credits nobody. Used by the tests and by operators as a smoke test of the whole lifecycle:
 * {@code /neroevents start neroevents:debug_marker}.
 */
public final class DebugMarkerPayload implements PayloadType<DebugMarkerPayload.Config, DebugMarkerPayload.State> {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "debug_marker");

    /** {@code {"ticks_to_resolve": int}}; absent = the definition's duration. */
    public record Config(Optional<Integer> ticksToResolve) implements PayloadConfig {

        static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.intRange(1, 1728000).optionalFieldOf("ticks_to_resolve").forGetter(Config::ticksToResolve)
        ).apply(inst, Config::new));
    }

    /** Active ticks counted so far. */
    public static final class State implements PayloadState {
        int ticks;

        public int ticks() {
            return ticks;
        }
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public MapCodec<Config> configCodec() {
        return Config.CODEC;
    }

    @Override
    public Footprint footprint(EventDefinition definition, Config config, Identifier dimension, BlockPos anchor) {
        return Footprint.point(dimension, anchor);
    }

    @Override
    public State createState(EventInstance instance, Config config) {
        return new State();
    }

    @Override
    public StartResult onStart(PayloadContext ctx, Config config, State state) {
        return StartResult.ok();
    }

    @Override
    public TickResult tick(PayloadContext ctx, Config config, State state) {
        state.ticks++;
        int total = config.ticksToResolve().orElse(ctx.instance().definition().durationTicks());
        if (state.ticks >= total) {
            return TickResult.resolved(EventOutcome.SUCCESS);
        }
        return TickResult.progress(state.ticks, total);
    }

    @Override
    public void onCleanup(PayloadContext ctx, Config config, State state) {
        // Nothing was placed or spawned.
    }

    @Override
    public CompoundTag saveState(State state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ticks", state.ticks);
        return tag;
    }

    @Override
    public State loadState(EventInstance instance, Config config, CompoundTag tag) {
        State state = new State();
        state.ticks = Math.max(0, tag.getIntOr("ticks", 0));
        return state;
    }

    @Override
    public ResumeResult onResume(PayloadContext ctx, Config config, State state) {
        return ResumeResult.ok();
    }
}
