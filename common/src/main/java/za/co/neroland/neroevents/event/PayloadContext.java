package za.co.neroland.neroevents.event;

import java.util.random.RandomGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.world.WorldView;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;

/**
 * What a payload may touch at runtime. Pure: payloads that need the level ({@code ServerLevel},
 * {@code MinecraftServer}) cast to {@link ServerPayloadContext}; tests use the pure part.
 */
public interface PayloadContext {

    EventInstance instance();

    WorldView world();

    Identifier dimension();

    @Nullable BlockPos anchor();

    /** The instance's bounds footprint (the whole dimension for {@code kind=dimension}). */
    Footprint bounds();

    /** Overworld game time. */
    long now();

    /** Seeded from the instance seed: deterministic per instance. */
    RandomGenerator random();

    /** Fresh for this tick; its cache lives for the tick. */
    ProtectionEvaluation protection();

    ContributionTable contributions();

    /** Progress for the HUD and companion app (signalled at most once per 20 ticks per instance). */
    void progress(int done, int total);

    /** A translatable mid-event broadcast (rate-limited to one per 100 ticks per instance). */
    void note(String key, Object... args);
}
