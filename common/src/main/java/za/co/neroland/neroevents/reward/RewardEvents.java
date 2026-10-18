package za.co.neroland.neroevents.reward;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.EventView;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The in-memory reward bus: after an instance pays out, listeners hear which participants were rewarded and
 * their own summaries (the companion app pushes "you earned …" to each contributor from here). The only
 * place a per-player map crosses a boundary, and it is synchronous, on the server thread and never stored;
 * the public {@code EventSignals} bus never names a player.
 */
public final class RewardEvents {

    /** One instance's payout: the public view and each rewarded participant's own summary. */
    public record RewardedBatch(EventView view, Map<UUID, RewardSummary> perPlayer) {

        public RewardedBatch {
            perPlayer = Map.copyOf(perPlayer);
        }
    }

    private static final List<Consumer<RewardedBatch>> LISTENERS = new CopyOnWriteArrayList<>();

    private RewardEvents() {
    }

    /** Common init slot 7b: the bus's registration point (listeners attach later). */
    public static void init() {
        // Intentionally empty: keeps the registration point visible in init order.
    }

    public static void onRewarded(Consumer<RewardedBatch> listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    /** Test teardown only. */
    public static void removeListener(Consumer<RewardedBatch> listener) {
        LISTENERS.remove(listener);
    }

    /** Delivers to every listener; a failing listener is isolated (one telemetry report, no player data). */
    static void publish(RewardedBatch batch) {
        for (Consumer<RewardedBatch> listener : LISTENERS) {
            try {
                listener.accept(batch);
            } catch (RuntimeException | LinkageError failure) {
                NeroEventsTelemetry.captureHandledException(failure, "rewards", "listener");
                NeroEventsCommon.LOGGER.warn("[NeroEvents] A reward listener failed ({}).", failure.getClass().getName());
            }
        }
    }
}
