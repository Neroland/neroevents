package za.co.neroland.neroevents.event;

import java.util.random.RandomGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.world.WorldView;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;
import za.co.neroland.neroevents.protect.ProtectionQuery;

/**
 * The pure {@link PayloadContext}: one per instance per tick, with a fresh protection evaluation.
 * Progress and notes are recorded on the instance; the engine turns them into coalesced signals and
 * rate-limited broadcasts after the payload returns.
 */
public class EnginePayloadContext implements PayloadContext {

    private final EventInstance instance;
    private final WorldView world;
    private final ProtectionEvaluation protection;
    private final EngineClock clock;

    public EnginePayloadContext(EventInstance instance, WorldView world, ProtectionQuery protection,
            EngineClock clock) {
        this.instance = instance;
        this.world = world;
        this.protection = protection.evaluation(world);
        this.clock = clock;
    }

    @Override
    public EventInstance instance() {
        return instance;
    }

    @Override
    public WorldView world() {
        return world;
    }

    @Override
    public Identifier dimension() {
        return instance.dimension();
    }

    @Override
    public @Nullable BlockPos anchor() {
        return instance.anchor();
    }

    @Override
    public Footprint bounds() {
        return instance.bounds();
    }

    @Override
    public long now() {
        return clock.now();
    }

    @Override
    public RandomGenerator random() {
        return instance.random();
    }

    @Override
    public ProtectionEvaluation protection() {
        return protection;
    }

    @Override
    public ContributionTable contributions() {
        return instance.contributions();
    }

    @Override
    public void progress(int done, int total) {
        instance.recordProgress(done, total);
    }

    @Override
    public void note(String key, Object... args) {
        instance.queueNote(key, args);
    }
}
