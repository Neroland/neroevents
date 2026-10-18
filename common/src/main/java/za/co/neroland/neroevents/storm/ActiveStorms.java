package za.co.neroland.neroevents.storm;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraft.resources.Identifier;

/**
 * Dimension &rarr; the storm currently active there (one storm per dimension). The read side for
 * {@code AgricultureCompat}, {@code /neroevents status} and the console; written only by the storm
 * payloads ({@link #set} at start and resume, {@link #clear} at cleanup).
 *
 * <p>In memory, server-scoped: a restart rebuilds it from the resumed instances. Thread-safe (the
 * NeroAgriculture cycle provider may read it from a worker thread).
 */
public final class ActiveStorms {

    /**
     * One active storm. {@code agricultureEnabled} is the instance's {@code agriculture_enabled} config flag,
     * carried here so {@code AgricultureCompat} needs no access to storm instances.
     */
    public record StormStatus(StormVariant variant, int intensity, UUID instanceId, boolean agricultureEnabled) {
    }

    private static final Map<Identifier, StormStatus> ACTIVE = new TreeMap<>();

    private ActiveStorms() {
    }

    /** The storm active in {@code dimension}, if any. */
    public static synchronized Optional<StormStatus> active(Identifier dimension) {
        return dimension == null ? Optional.empty() : Optional.ofNullable(ACTIVE.get(dimension));
    }

    /** Every active storm by dimension (status lines); an unmodifiable snapshot. */
    public static synchronized Map<Identifier, StormStatus> all() {
        return Map.copyOf(ACTIVE);
    }

    /**
     * Records the storm in {@code dimension}. Refused (returns {@code false}) when a different instance
     * already holds the dimension; re-setting the same instance (resume, intensity change) succeeds.
     */
    static synchronized boolean set(Identifier dimension, StormStatus status) {
        StormStatus held = ACTIVE.get(dimension);
        if (held != null && !held.instanceId().equals(status.instanceId())) {
            return false;
        }
        ACTIVE.put(dimension, status);
        return true;
    }

    /** Forgets the storm in {@code dimension} if (and only if) {@code instanceId} holds it. */
    static synchronized void clear(Identifier dimension, UUID instanceId) {
        StormStatus held = ACTIVE.get(dimension);
        if (held != null && held.instanceId().equals(instanceId)) {
            ACTIVE.remove(dimension);
        }
    }

    /** Test isolation (any package's tests, e.g. the agriculture façade's). */
    public static synchronized void resetForTests() {
        ACTIVE.clear();
    }

    /** Tests only: puts a storm in {@code dimension} as a running instance would. */
    public static synchronized void putForTests(Identifier dimension, StormStatus status) {
        ACTIVE.put(dimension, status);
    }
}
