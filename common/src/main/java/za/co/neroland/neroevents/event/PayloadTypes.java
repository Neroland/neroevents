package za.co.neroland.neroevents.event;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.resources.Identifier;

/**
 * The payload-type registry. Core-only types register in common init slot 3; sibling façades register
 * theirs in slot 5, and only when the sibling is loaded. The registry freezes on the first server tick.
 *
 * <p>Type ids are resolved when definitions load, so a definition whose type is not registered (a
 * sibling's event on a server without the sibling) is dropped loudly and the pool is right by
 * construction.
 */
public final class PayloadTypes {

    private static final Map<Identifier, PayloadType<?, ?>> TYPES = new TreeMap<>();
    private static boolean frozen;

    private PayloadTypes() {
    }

    /** Adds a type. Init-time only: a duplicate id or a registration after freezing throws. */
    public static synchronized void register(PayloadType<?, ?> type) {
        if (frozen) {
            throw new IllegalStateException("Payload types are frozen; register " + type.id() + " during init");
        }
        if (TYPES.containsKey(type.id())) {
            throw new IllegalStateException("Duplicate payload type " + type.id());
        }
        TYPES.put(type.id(), type);
    }

    public static synchronized Optional<PayloadType<?, ?>> get(Identifier id) {
        return Optional.ofNullable(TYPES.get(id));
    }

    public static synchronized Set<Identifier> ids() {
        return Set.copyOf(TYPES.keySet());
    }

    /** Called by the engine on its first server tick. */
    public static synchronized void freeze() {
        frozen = true;
    }

    public static synchronized boolean frozen() {
        return frozen;
    }

    /** Test isolation. */
    public static synchronized void resetForTests() {
        TYPES.clear();
        frozen = false;
    }
}
