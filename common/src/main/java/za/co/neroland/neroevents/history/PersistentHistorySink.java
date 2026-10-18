package za.co.neroland.neroevents.history;

import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.EventView;
import za.co.neroland.neroevents.event.reward.EventHistorySink;
import za.co.neroland.neroevents.reward.RewardOutcome;

/**
 * Writes every instance that ran, was skipped or was cancelled to {@link EventHistoryState} as an aggregate
 * {@link HistoryEntry}: why nothing happened stays visible, and nothing in the entry names a player.
 */
public final class PersistentHistorySink implements EventHistorySink {

    private final Supplier<EventHistoryState> store;
    private final LongSupplier wallClock;
    private final IntSupplier maxEntries;

    public PersistentHistorySink(Supplier<EventHistoryState> store, LongSupplier wallClock, IntSupplier maxEntries) {
        this.store = store;
        this.wallClock = wallClock;
        this.maxEntries = maxEntries;
    }

    @Override
    public void record(EventView aggregate) {
        // The engine always calls record(EventInstance, long); a bare view carries too little to store.
    }

    @Override
    public void record(EventInstance instance, long now) {
        store.get().record(entryFor(instance, wallClock.getAsLong()), maxEntries.getAsInt());
    }

    /** The aggregate entry for {@code instance} at {@code endedAt} (epoch ms). Pure. */
    public static HistoryEntry entryFor(EventInstance instance, long endedAt) {
        EventPhase terminal = switch (instance.phase()) {
            case SKIPPED -> EventPhase.SKIPPED;
            case CANCELLED -> EventPhase.CANCELLED;
            default -> EventPhase.DONE;
        };
        RewardOutcome rewards = instance.rewardOutcome();
        long startedAt = instance.wallStartedAt() > 0L ? instance.wallStartedAt() : instance.wallCreatedAt();
        return new HistoryEntry(instance.id(), instance.definition().id(), instance.definition().type(),
                instance.definition().category(), instance.dimension(), instance.origin(), startedAt, endedAt, terminal,
                Optional.ofNullable(instance.outcome()), Optional.ofNullable(instance.reason()),
                instance.contributions().participantCount(), rewards.chargedValue(), rewards.currencyPaid(),
                rewards.xpGranted(), rewards.itemStacks(), instance.votes().yes(), instance.votes().no());
    }
}
