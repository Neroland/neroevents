package za.co.neroland.neroevents.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.PayloadConfig;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.PayloadState;
import za.co.neroland.neroevents.event.PayloadType;
import za.co.neroland.neroevents.event.ResumeResult;
import za.co.neroland.neroevents.event.StartResult;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.protect.Footprint;

/** A payload type that records every callback and returns scripted results. */
public final class RecordingPayloadType implements PayloadType<RecordingPayloadType.Config, RecordingPayloadType.State> {

    public record Config() implements PayloadConfig {
    }

    public static final class State implements PayloadState {
        public int ticks;
    }

    private final Identifier id;
    public final List<String> calls = new ArrayList<>();
    public StartResult start = StartResult.ok();
    /** Active tick number (1-based) → result. */
    public IntFunction<TickResult> script = n -> TickResult.proceed();
    public boolean throwOnTick;
    public boolean throwOnLoad;
    public ResumeResult resume = ResumeResult.ok();
    public EventOutcome timeout = EventOutcome.EXPIRED;
    public int footprintRadius;
    /** What {@code pickTarget} returns ({@code bounds.kind = target}). */
    public Optional<BlockPos> target = Optional.empty();

    public RecordingPayloadType(String path) {
        this.id = Identifier.fromNamespaceAndPath("neroevents_test", path);
    }

    @Override
    public Identifier id() {
        return id;
    }

    @Override
    public MapCodec<Config> configCodec() {
        return MapCodec.unit(new Config());
    }

    @Override
    public Footprint footprint(EventDefinition definition, Config config, Identifier dimension, BlockPos anchor) {
        return Footprint.cylinder(dimension, anchor, footprintRadius, anchor.getY(), anchor.getY());
    }

    @Override
    public Optional<BlockPos> pickTarget(PayloadContext ctx, Config config) {
        calls.add("pickTarget");
        return target;
    }

    @Override
    public State createState(EventInstance instance, Config config) {
        calls.add("createState");
        return new State();
    }

    @Override
    public StartResult onStart(PayloadContext ctx, Config config, State state) {
        calls.add("onStart");
        return start;
    }

    @Override
    public TickResult tick(PayloadContext ctx, Config config, State state) {
        calls.add("tick");
        if (throwOnTick) {
            throw new IllegalStateException("tick failure");
        }
        state.ticks++;
        return script.apply(state.ticks);
    }

    @Override
    public EventOutcome onTimeout(PayloadContext ctx, Config config, State state) {
        calls.add("onTimeout");
        return timeout;
    }

    @Override
    public void onCleanup(PayloadContext ctx, Config config, State state) {
        calls.add("onCleanup");
    }

    @Override
    public CompoundTag saveState(State state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ticks", state.ticks);
        return tag;
    }

    @Override
    public State loadState(EventInstance instance, Config config, CompoundTag tag) {
        calls.add("loadState");
        if (throwOnLoad) {
            throw new IllegalStateException("load failure");
        }
        State state = new State();
        state.ticks = tag.getIntOr("ticks", 0);
        return state;
    }

    @Override
    public ResumeResult onResume(PayloadContext ctx, Config config, State state) {
        calls.add("onResume");
        return resume;
    }

    public long count(String call) {
        return calls.stream().filter(call::equals).count();
    }
}
