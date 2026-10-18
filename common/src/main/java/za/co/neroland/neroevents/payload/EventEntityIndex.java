package za.co.neroland.neroevents.payload;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

/**
 * Entity UUID → event instance id, in memory, server-scoped. Lets a hook outside the tick (a living-damage
 * event, an entity's own tick) find the instance an entity belongs to with one map lookup.
 *
 * <p>Never saved: our own entities carry the instance id as a saved field, sibling mobs carry NeroCreatures'
 * persistent wave id, and every payload rebuilds its entries in {@code onResume}. A new server clears the
 * map by identity ({@link #resetForServer}), which every payload calls from {@code onStart}/{@code onResume}
 * (cheap: one reference compare). Holds entity and instance ids only — never a player.
 */
public final class EventEntityIndex {

    private static final Map<UUID, UUID> BY_ENTITY = new ConcurrentHashMap<>();
    private static WeakReference<Object> owner = new WeakReference<>(null);

    private EventEntityIndex() {
    }

    /** Records that {@code entity} belongs to {@code instance} (replacing any earlier binding). */
    public static void bind(UUID entity, UUID instance) {
        if (entity != null && instance != null) {
            BY_ENTITY.put(entity, instance);
        }
    }

    /** Forgets one entity. */
    public static void unbind(UUID entity) {
        if (entity != null) {
            BY_ENTITY.remove(entity);
        }
    }

    /** The instance this entity belongs to, if any. */
    public static Optional<UUID> instanceOf(UUID entity) {
        return entity == null ? Optional.empty() : Optional.ofNullable(BY_ENTITY.get(entity));
    }

    /** Forgets every entity bound to {@code instance} (cleanup). */
    public static void clearInstance(UUID instance) {
        if (instance != null) {
            BY_ENTITY.values().removeIf(instance::equals);
        }
    }

    /** Identity check: a server other than the one the map was built for clears the map. */
    public static void resetForServer(MinecraftServer server) {
        resetForIdentity(server);
    }

    /** The identity rule behind {@link #resetForServer}, over any object (tests pass plain objects). */
    static synchronized void resetForIdentity(@Nullable Object server) {
        if (owner.get() != server) {
            BY_ENTITY.clear();
            owner = new WeakReference<>(server);
        }
    }

    /** How many entities are bound (tests and diagnostics). */
    public static int size() {
        return BY_ENTITY.size();
    }

    /** Empties the map and forgets the owning server. Tests only. */
    static synchronized void resetForTests() {
        BY_ENTITY.clear();
        owner = new WeakReference<>(null);
    }
}
