package za.co.neroland.neroevents.event;

import org.jetbrains.annotations.Nullable;

/** The answer to an {@link EventRequest}: queued with its instance, or refused with a code. */
public sealed interface RequestResult {

    /** The instance was created and is counting down. */
    record Queued(EventInstance instance) implements RequestResult {
    }

    /**
     * Refused. {@code code} is one of {@code unknown_definition}, {@code payload_missing},
     * {@code kill_switch}, {@code disabled}, {@code calm_window}, {@code band_cap}, {@code type_cap},
     * {@code protected}, {@code no_anchor}, {@code no_target}, {@code unknown_dimension}; {@code message}
     * is a short, non-identifying detail (a source id, a config key).
     */
    record Refused(String code, @Nullable String message) implements RequestResult {
    }
}
