package za.co.neroland.neroevents.event;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The engine-wide lifecycle bus. Subscribers (the companion-app module, the HUD) subscribe once and map
 * {@link EventSignal.Kind} to their own topics; every signal carries the instance id inside its view.
 * {@code PROGRESS} and {@code VOTE_CHANGED} are coalesced by the engine to at most one per instance per
 * 20 ticks.
 *
 * <p>Isolation: a throwing subscriber is caught, warned about once per subscriber class, and the rest
 * still receive the signal. Server thread only.
 */
public final class EventSignals {

    private static final List<Consumer<EventSignal>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private EventSignals() {
    }

    public static void subscribe(Consumer<EventSignal> listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void unsubscribe(Consumer<EventSignal> listener) {
        LISTENERS.remove(listener);
    }

    static void publish(EventSignal signal) {
        for (Consumer<EventSignal> listener : LISTENERS) {
            try {
                listener.accept(signal);
            } catch (RuntimeException | LinkageError failure) {
                String name = listener.getClass().getName();
                if (WARNED.add(name)) {
                    NeroEventsCommon.LOGGER.warn("[NeroEvents] An event-signal listener ({}) failed ({}); "
                            + "other listeners still run.", name, failure.getClass().getName());
                    NeroEventsTelemetry.captureHandledException(failure, "engine", "signal_listener");
                }
            }
        }
    }
}
