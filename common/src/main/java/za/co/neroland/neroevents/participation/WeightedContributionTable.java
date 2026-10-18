package za.co.neroland.neroevents.participation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.ContributionTable;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.reward.RewardShare;
import za.co.neroland.neroevents.reward.RewardSplit;

/**
 * The contribution table: a weighted, decaying, in-memory map from player UUID to what they did during
 * one instance. Fed only through the payload API ({@link Contributions}) from attributable actions.
 *
 * <ul>
 *   <li><b>Weights:</b> each kind is multiplied by the policy's weight (damage per point, loot per open,
 *       objective per capture, survival per wave).</li>
 *   <li><b>Once-keys:</b> a container, pocket or pod credits a player once ({@link #creditOnce}); at most
 *       {@value #ONCE_KEY_CAP} keys per entry.</li>
 *   <li><b>Credit floor:</b> an entry below {@code creditFloor} is not a participant.</li>
 *   <li><b>Survival needs action:</b> survival credit is ignored for a player with no damage, loot or
 *       objective weight in this instance (an AFK account inside the bounds earns nothing).</li>
 *   <li><b>Decay:</b> an entry outside the bounds or offline for {@code inactivityTicks} is multiplied by
 *       {@code decayFactor} per further window and dropped below {@code minWeight}. Re-entering resets
 *       the clock, never the weight.</li>
 * </ul>
 *
 * <p>Nothing here is a position or a wall-clock time ({@code outsideSince} is instance game time and dies
 * with the instance). Server thread only.
 */
public final class WeightedContributionTable implements ContributionTable {

    public static final int ONCE_KEY_CAP = 256;
    private static final long SCALE = RewardSplit.SCALE;

    /** One player's entry. */
    static final class Contribution {
        double weight;
        final EnumMap<Kind, Double> perKind = new EnumMap<>(Kind.class);
        long outsideSince = -1L;
        long decayWindowsApplied;
        final Set<String> onceKeys = new LinkedHashSet<>();

        boolean hasActionWeight() {
            return perKind.containsKey(Kind.DAMAGE) || perKind.containsKey(Kind.LOOT)
                    || perKind.containsKey(Kind.OBJECTIVE);
        }
    }

    private final ContributionPolicy policy;
    private ContributionListener listener;
    private final Map<UUID, Contribution> entries = new HashMap<>();
    /** Players credited at least once in this instance (so a decayed-and-dropped entry does not re-fire). */
    private final Set<UUID> firstCredited = new HashSet<>();
    private boolean onceCapLogged;

    public WeightedContributionTable(ContributionPolicy policy, ContributionListener listener) {
        this.policy = policy == null ? ContributionPolicy.DEFAULT : policy;
        this.listener = listener == null ? ContributionListener.NONE : listener;
    }

    public ContributionPolicy policy() {
        return policy;
    }

    /** The engine attaches its participation listener after creating the instance. */
    public void listener(ContributionListener value) {
        this.listener = value == null ? ContributionListener.NONE : value;
    }

    private static boolean valid(UUID player, Kind kind, double amount) {
        return player != null && kind != null && amount > 0.0D && !Double.isInfinite(amount) && !Double.isNaN(amount);
    }

    @Override
    public void credit(UUID player, Kind kind, double amount) {
        if (!valid(player, kind, amount)) {
            return;
        }
        if (kind == Kind.SURVIVAL) {
            Contribution existing = entries.get(player);
            if (existing == null || !existing.hasActionWeight()) {
                return;
            }
        }
        apply(player, kind, amount);
    }

    @Override
    public boolean creditOnce(UUID player, Kind kind, String onceKey, double amount) {
        if (!valid(player, kind, amount) || onceKey == null) {
            return false;
        }
        Contribution existing = entries.get(player);
        if (kind == Kind.SURVIVAL && (existing == null || !existing.hasActionWeight())) {
            return false;
        }
        String key = kind.name() + ":" + onceKey;
        if (existing != null) {
            if (existing.onceKeys.contains(key)) {
                return false;
            }
            if (existing.onceKeys.size() >= ONCE_KEY_CAP) {
                if (!onceCapLogged) {
                    onceCapLogged = true;
                    NeroEventsCommon.LOGGER.debug("[NeroEvents] A contribution entry reached {} once-keys; further "
                            + "once-credits are ignored for it.", ONCE_KEY_CAP);
                }
                return false;
            }
        }
        Contribution entry = apply(player, kind, amount);
        entry.onceKeys.add(key);
        return true;
    }

    private Contribution apply(UUID player, Kind kind, double amount) {
        Contribution entry = entries.computeIfAbsent(player, p -> new Contribution());
        entry.weight += policy.weightOf(kind, amount);
        entry.perKind.merge(kind, amount, Double::sum);
        entry.outsideSince = -1L;
        entry.decayWindowsApplied = 0L;
        if (firstCredited.add(player)) {
            listener.onFirstCredit(player);
        }
        return entry;
    }

    private boolean isCredited(Contribution entry) {
        return entry.weight >= policy.creditFloor() && entry.weight > 0.0D;
    }

    @Override
    public int participantCount() {
        int count = 0;
        for (Contribution entry : entries.values()) {
            if (isCredited(entry)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public double share(UUID player) {
        Contribution own = player == null ? null : entries.get(player);
        if (own == null || !isCredited(own)) {
            return 0.0D;
        }
        double total = 0.0D;
        for (Contribution entry : entries.values()) {
            if (isCredited(entry)) {
                total += entry.weight;
            }
        }
        return total <= 0.0D ? 0.0D : own.weight / total;
    }

    @Override
    public Set<UUID> participants() {
        Set<UUID> out = new HashSet<>();
        for (Map.Entry<UUID, Contribution> entry : entries.entrySet()) {
            if (isCredited(entry.getValue())) {
                out.add(entry.getKey());
            }
        }
        return Set.copyOf(out);
    }

    @Override
    public void forget(UUID player) {
        entries.remove(player);
        firstCredited.remove(player);
    }

    @Override
    public void clear() {
        entries.clear();
        firstCredited.clear();
    }

    @Override
    public void sweep(long now, PresenceView presence, Footprint bounds) {
        if (presence == null || bounds == null) {
            return;
        }
        Iterator<Map.Entry<UUID, Contribution>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Contribution> row = it.next();
            UUID uuid = row.getKey();
            Contribution entry = row.getValue();
            boolean inside = presence.isOnline(uuid) && (bounds.wholeDimension()
                    ? presence.inDimension(uuid, bounds.dimension()) : presence.isWithin(uuid, bounds));
            if (inside) {
                entry.outsideSince = -1L;
                entry.decayWindowsApplied = 0L;
                continue;
            }
            if (entry.outsideSince < 0L) {
                entry.outsideSince = now;
                continue;
            }
            if (!policy.decay() || policy.inactivityTicks() <= 0) {
                continue;
            }
            long windows = (now - entry.outsideSince) / policy.inactivityTicks();
            if (windows >= 1L && windows > entry.decayWindowsApplied) {
                for (long i = entry.decayWindowsApplied; i < windows; i++) {
                    entry.weight *= policy.decayFactor();
                }
                entry.decayWindowsApplied = windows;
                if (entry.weight < policy.minWeight()) {
                    it.remove(); // firstCredited keeps the id, so first_participation is not re-fired
                }
            }
        }
    }

    @Override
    public long weightMilli(UUID player) {
        Contribution entry = player == null ? null : entries.get(player);
        return entry == null ? 0L : Math.round(entry.weight * 1000.0D);
    }

    @Override
    public List<RewardShare> ranked() {
        record Row(UUID player, long weightMilli) {
        }
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<UUID, Contribution> entry : entries.entrySet()) {
            if (isCredited(entry.getValue())) {
                rows.add(new Row(entry.getKey(), Math.round(entry.getValue().weight * 1000.0D)));
            }
        }
        rows.sort(Comparator.comparingLong(Row::weightMilli).reversed().thenComparing(Row::player));
        long total = 0L;
        for (Row row : rows) {
            total += row.weightMilli();
        }
        long eligiblePpm = Math.round(policy.floorEligibleRawShare() * SCALE);
        List<RewardShare> out = new ArrayList<>(rows.size());
        int rank = 1;
        for (Row row : rows) {
            long raw = total <= 0L ? 0L : RewardSplit.mulDiv(row.weightMilli(), SCALE, total);
            out.add(new RewardShare(row.player(), rank++, row.weightMilli(), raw, raw >= eligiblePpm));
        }
        return List.copyOf(out);
    }

    @Override
    public Map<Kind, Double> breakdown(UUID player) {
        Contribution entry = player == null ? null : entries.get(player);
        return entry == null ? Map.of() : Map.copyOf(entry.perKind);
    }

    @Override
    public boolean holds(UUID player) {
        return player != null && (entries.containsKey(player) || firstCredited.contains(player));
    }
}
