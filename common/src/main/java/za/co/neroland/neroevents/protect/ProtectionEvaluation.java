package za.co.neroland.neroevents.protect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.world.WorldView;

/**
 * One effect's (or one payload tick's) protection evaluation: asks every source in order and memoises
 * chunk-granular answers for its own lifetime through {@link #cached}. Create a fresh one per effect or
 * per tick — never keep one across ticks, the world changes.
 *
 * <p>Deny wins. A source that throws ({@code RuntimeException} or {@code LinkageError}) counts as a
 * refusal, is marked failing in {@link ProtectionSources#health()} and is warned about once per
 * {@code (source, exception class)}.
 */
public final class ProtectionEvaluation {

    private final WorldView world;
    private final List<ProtectionSource> sources;
    private final Map<Object, Object> cache = new HashMap<>();
    private @Nullable String note;

    ProtectionEvaluation(WorldView world, List<ProtectionSource> sources) {
        this.world = world;
        this.sources = sources;
    }

    public WorldView world() {
        return world;
    }

    /** Whether the effect may touch this one block. */
    public ProtectionResult mayAffect(Identifier dimension, BlockPos pos) {
        return mayAffectAll(Footprint.point(dimension, pos));
    }

    /** Whether the effect may touch every block of the footprint. */
    public ProtectionResult mayAffectAll(Footprint footprint) {
        for (ProtectionSource source : sources) {
            note = null;
            Verdict verdict;
            try {
                verdict = source.check(this, footprint);
            } catch (RuntimeException | LinkageError failure) {
                ProtectionSources.markFailing(source.id(), failure);
                return ProtectionResult.denied(source.id(), "failing");
            }
            if (verdict == Verdict.DENY) {
                return ProtectionResult.denied(source.id(), note);
            }
        }
        return ProtectionResult.ALLOWED;
    }

    /** Per-evaluation memo for chunk-granular or footprint-granular answers. */
    @SuppressWarnings("unchecked")
    public <T> T cached(Object key, Supplier<T> compute) {
        Object hit = cache.get(key);
        if (hit != null) {
            return (T) hit;
        }
        T value = compute.get();
        if (value != null) {
            cache.put(key, value);
        }
        return value;
    }

    /** A short, non-identifying detail for the refusal the calling source is about to return. */
    public void note(String detail) {
        this.note = detail;
    }
}
