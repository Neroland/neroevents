package za.co.neroland.neroevents.participation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.progression.Milestones;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * One per engine: runs the contribution decay sweep for active instances, fires {@code first_participation}
 * on a player's first credit, and owns the session-only "already paid" markers used for opted-out players
 * and for a server that keeps no reward ledger. Everything here is in memory and erased per player.
 * Server thread only; a sweep is O(participants) and touches no chunk or entity.
 */
public final class ParticipationService {

    private final Supplier<Milestones> milestones;
    private final IntSupplier sweepIntervalTicks;
    private final Set<String> paidThisSession = new HashSet<>();
    private long lastSweepAt = Long.MIN_VALUE;

    public ParticipationService(Supplier<Milestones> milestones, IntSupplier sweepIntervalTicks) {
        this.milestones = milestones;
        this.sweepIntervalTicks = sweepIntervalTicks;
    }

    /** The listener the engine attaches to an instance's contribution table. */
    public ContributionListener listenerFor(EventInstance instance) {
        return player -> {
            try {
                milestones.get().recordFirstParticipation(player);
            } catch (RuntimeException | LinkageError failure) {
                NeroEventsTelemetry.captureHandledException(failure, "participation", "first_participation");
            }
        };
    }

    /** Engine tick: every {@code contributionSweepIntervalTicks}, sweeps each active instance's table. */
    public void tick(List<EventInstance> live, long now, @Nullable PresenceView presence) {
        int interval = Math.max(1, sweepIntervalTicks.getAsInt());
        if (presence == null || (lastSweepAt != Long.MIN_VALUE && now - lastSweepAt < interval)) {
            return;
        }
        lastSweepAt = now;
        for (EventInstance instance : live) {
            if (instance.phase() == EventPhase.ACTIVE) {
                try {
                    instance.contributions().sweep(now, presence, instance.bounds());
                } catch (RuntimeException | LinkageError failure) {
                    NeroEventsTelemetry.captureHandledException(failure, "participation", "sweep");
                }
            }
        }
    }

    private static String key(UUID instanceId, UUID player) {
        return instanceId + "|" + player;
    }

    public boolean paidThisSession(UUID instanceId, UUID player) {
        return paidThisSession.contains(key(instanceId, player));
    }

    public void markPaid(UUID instanceId, UUID player) {
        paidThisSession.add(key(instanceId, player));
    }

    /**
     * Drops every marker for {@code instanceId}: called when the instance leaves the engine's recent list,
     * after which it can never be resolved again, so the set stays bounded by what the engine still holds.
     */
    public void forgetInstance(UUID instanceId) {
        String prefix = instanceId + "|";
        paidThisSession.removeIf(k -> k.startsWith(prefix));
    }

    /** Erasure: drops the player's session markers. */
    public void forget(UUID player) {
        String suffix = "|" + player;
        paidThisSession.removeIf(k -> k.endsWith(suffix));
    }

    /** Conformance probe. */
    public boolean holds(UUID player) {
        String suffix = "|" + player;
        return paidThisSession.stream().anyMatch(k -> k.endsWith(suffix));
    }
}
