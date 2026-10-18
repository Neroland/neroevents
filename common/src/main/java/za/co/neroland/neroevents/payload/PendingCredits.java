package za.co.neroland.neroevents.payload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.ContributionTable.Kind;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.ServerPayloadContext;
import za.co.neroland.neroevents.participation.Contributions;

/**
 * A per-instance queue of credits earned <b>outside</b> the payload's own tick (a pod opened, a pocket mined,
 * a crate looted, a mob hurt, a wave survived), drained into the instance's contribution table at the top of
 * the next tick, so {@link PayloadContext} is only ever used inside the engine's tick.
 *
 * <p>In memory only: a payload state holds one, and it is never saved (a restart drops what was queued —
 * at most one tick's worth). Bounded at {@link #CAP} entries; further adds are dropped (logged once at DEBUG,
 * with no player identity). Server thread only, but synchronised so a stray off-thread hook cannot corrupt it.
 */
public final class PendingCredits {

    /** The queue bound: once this many credits wait, further adds are dropped. */
    public static final int CAP = 1024;

    /**
     * One queued credit. {@code player} is null for damage (the damage source names the attacker);
     * {@code attribution} carries the pod UUID of a supply claim, the wave index of a survival credit, or
     * the {@link DamageSource} of a damage credit.
     */
    public record Pending(@Nullable UUID player, Kind kind, @Nullable String onceKey, double amount,
            @Nullable Object attribution) {
    }

    /**
     * Where drained credits go. Production is {@link #contributions(PayloadContext)} (the
     * {@link Contributions} API); tests pass a recorder. Each method returns whether it credited.
     *
     * @param <P> the resolved player type ({@link ServerPlayer} in game)
     */
    public interface Sink<P> {

        boolean loot(P player, String onceKey);

        boolean supplyClaim(P player, UUID podUuid);

        boolean objective(P player, double fraction);

        boolean survival(P player, int waveIndex);

        boolean damage(DamageSource source, float amount);
    }

    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private boolean overflowLogged;

    /** LOOT, drained through {@code Contributions.creditLoot(ctx, ServerPlayer, onceKey)}. */
    public void loot(UUID player, String onceKey) {
        if (player == null || onceKey == null) {
            return;
        }
        add(new Pending(player, Kind.LOOT, onceKey, 1.0D, null));
    }

    /** A pod's first open, drained through {@code Contributions.creditSupplyClaim(ctx, player, podUuid)}. */
    public void supplyClaim(UUID player, UUID podUuid) {
        if (player == null || podUuid == null) {
            return;
        }
        add(new Pending(player, Kind.LOOT, "pod:" + podUuid, 1.0D, podUuid));
    }

    /** OBJECTIVE, drained through {@code Contributions.creditObjective}. */
    public void objective(UUID player, double fraction) {
        if (player == null) {
            return;
        }
        add(new Pending(player, Kind.OBJECTIVE, null, fraction, null));
    }

    /** SURVIVAL for one wave, drained through {@code Contributions.creditSurvival}. */
    public void survival(UUID player, int waveIndex) {
        if (player == null) {
            return;
        }
        add(new Pending(player, Kind.SURVIVAL, "wave:" + waveIndex, 1.0D, Integer.valueOf(waveIndex)));
    }

    /** DAMAGE, drained through {@code Contributions.creditDamage(ctx, source, amount)}. */
    public void damage(DamageSource source, float amount) {
        if (source == null || !(amount > 0.0F)) {
            return;
        }
        add(new Pending(null, Kind.DAMAGE, null, amount, source));
    }

    /**
     * Drains every queued credit into the instance: each player UUID is resolved through
     * {@code ctx.server().getPlayerList().getPlayer(uuid)}; an absent (offline) player's credit is dropped.
     * Returns how many credits landed.
     */
    public int drainInto(ServerPayloadContext ctx) {
        return drainInto(uuid -> ctx.server().getPlayerList().getPlayer(uuid), contributions(ctx));
    }

    /**
     * The seam behind {@link #drainInto(ServerPayloadContext)}: drains in queue order, resolving players with
     * {@code resolver} (null ⇒ dropped) and crediting through {@code sink}. The queue is empty afterwards, so
     * each credit is applied at most once. Returns how many credits the sink accepted.
     */
    public <P> int drainInto(Function<UUID, ? extends P> resolver, Sink<P> sink) {
        List<Pending> batch;
        synchronized (this) {
            if (queue.isEmpty()) {
                return 0;
            }
            batch = new ArrayList<>(queue);
            queue.clear();
        }
        int credited = 0;
        for (Pending pending : batch) {
            if (apply(pending, resolver, sink)) {
                credited++;
            }
        }
        return credited;
    }

    private static <P> boolean apply(Pending pending, Function<UUID, ? extends P> resolver, Sink<P> sink) {
        if (pending.kind() == Kind.DAMAGE) {
            return pending.attribution() instanceof DamageSource source
                    && sink.damage(source, (float) pending.amount());
        }
        UUID id = pending.player();
        P player = id == null ? null : resolver.apply(id);
        if (player == null) {
            return false; // offline (or gone): the credit is dropped
        }
        if (pending.kind() == Kind.LOOT) {
            return pending.attribution() instanceof UUID pod
                    ? sink.supplyClaim(player, pod)
                    : sink.loot(player, pending.onceKey());
        }
        if (pending.kind() == Kind.OBJECTIVE) {
            return sink.objective(player, pending.amount());
        }
        return pending.attribution() instanceof Integer wave && sink.survival(player, wave.intValue());
    }

    /** How many credits are waiting. */
    public synchronized int size() {
        return queue.size();
    }

    /** A snapshot of the queue in order (tests and diagnostics). */
    public synchronized List<Pending> snapshot() {
        return List.copyOf(queue);
    }

    /** Drops everything queued (cleanup). */
    public synchronized void clear() {
        queue.clear();
    }

    private synchronized void add(Pending pending) {
        if (queue.size() >= CAP) {
            if (!overflowLogged) {
                overflowLogged = true;
                NeroEventsCommon.LOGGER.debug("[NeroEvents] Pending credit queue full ({}); further credits dropped.",
                        CAP);
            }
            return;
        }
        queue.addLast(pending);
    }

    /** The production sink: the {@link Contributions} API over the instance's context. */
    public static Sink<ServerPlayer> contributions(PayloadContext ctx) {
        return new Sink<>() {
            @Override
            public boolean loot(ServerPlayer player, String onceKey) {
                return Contributions.creditLoot(ctx, player, onceKey);
            }

            @Override
            public boolean supplyClaim(ServerPlayer player, UUID podUuid) {
                return Contributions.creditSupplyClaim(ctx, player, podUuid);
            }

            @Override
            public boolean objective(ServerPlayer player, double fraction) {
                Contributions.creditObjective(ctx, player, fraction);
                return true;
            }

            @Override
            public boolean survival(ServerPlayer player, int waveIndex) {
                return Contributions.creditSurvival(ctx, player, waveIndex);
            }

            @Override
            public boolean damage(DamageSource source, float amount) {
                return Contributions.creditDamage(ctx, source, amount) > 0.0D;
            }
        };
    }
}
