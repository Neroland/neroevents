package za.co.neroland.neroevents.reward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Reward crate logic: admitting a grant under the per-player stack cap, claiming into an inventory, and
 * the <b>session crate</b> for players who opted out of stored reward history (kept in memory, lost on
 * restart, cleared on a server change and by erasure).
 *
 * <p>The split between pure logic and Minecraft is deliberate: {@link #admit} and {@link #apply} are pure
 * planners over plain records and an {@link InventoryModel}, so the claim and cap rules are plain-JVM
 * tested; {@link #claim(ServerPlayer)} is the one adapter that builds item stacks and touches the player's
 * inventory. Nothing is ever dropped into the world, not even on a full inventory.
 */
public final class RewardCrates {

    /** What an inventory accepts: how many of {@code count} fit. */
    public interface InventoryModel {
        int accept(Identifier item, int count);
    }

    public enum Status { CLAIMED, PARTIAL, EMPTY, NOT_ONLINE }

    public record ClaimResult(int stacksGiven, int stacksRemaining, long xpGiven, Status status) {
    }

    /** The outcome of {@link #apply}: what is left, and what was handed over. */
    public record ClaimPlan(List<CrateEntry> remaining, int stacksGiven, int stacksRemaining, long xpGiven) {
    }

    /** The outcome of {@link #admit}: the stacks that fit and how many items were forfeited. */
    public record Admission(List<CrateEntry.Stack> stored, int forfeitedStacks, int forfeitedItems) {
    }

    private final Supplier<RewardCrateState> store;
    private final Map<UUID, List<CrateEntry>> sessionCrates = new HashMap<>();
    private final Set<UUID> toldSession = new HashSet<>();

    public RewardCrates(Supplier<RewardCrateState> store) {
        this.store = store;
    }

    // --- pure planners --------------------------------------------------------------------------------

    /**
     * Merges same-item reward stacks (up to {@link CrateEntry#MAX_STACK} each) and keeps as many as fit
     * beside {@code existingStacks} under {@code cap}; the rest is forfeited.
     */
    public static Admission admit(int existingStacks, List<CrateEntry.Stack> incoming, int cap) {
        Map<Identifier, Integer> totals = new LinkedHashMap<>();
        for (CrateEntry.Stack stack : incoming) {
            if (stack != null && stack.count() > 0) {
                totals.merge(stack.item(), stack.count(), Integer::sum);
            }
        }
        List<CrateEntry.Stack> merged = new ArrayList<>();
        totals.forEach((item, count) -> {
            int left = count;
            while (left > 0) {
                int n = Math.min(CrateEntry.MAX_STACK, left);
                merged.add(new CrateEntry.Stack(item, n));
                left -= n;
            }
        });
        int room = Math.max(0, cap - existingStacks);
        List<CrateEntry.Stack> stored = new ArrayList<>(merged.subList(0, Math.min(room, merged.size())));
        int forfeitedStacks = merged.size() - stored.size();
        int forfeitedItems = 0;
        for (int i = stored.size(); i < merged.size(); i++) {
            forfeitedItems += merged.get(i).count();
        }
        return new Admission(List.copyOf(stored), forfeitedStacks, forfeitedItems);
    }

    /**
     * Hands every entry (oldest first) to {@code inventory}: what does not fit stays in its entry, in order;
     * experience is always granted in full. Pure.
     */
    public static ClaimPlan apply(List<CrateEntry> entries, InventoryModel inventory) {
        List<CrateEntry> remaining = new ArrayList<>();
        int given = 0;
        int left = 0;
        long xp = 0L;
        for (CrateEntry entry : entries) {
            List<CrateEntry.Stack> kept = new ArrayList<>();
            for (CrateEntry.Stack stack : entry.items()) {
                int accepted = Math.max(0, Math.min(stack.count(), inventory.accept(stack.item(), stack.count())));
                if (accepted >= stack.count()) {
                    given++;
                } else {
                    if (accepted > 0) {
                        given++;
                    }
                    kept.add(new CrateEntry.Stack(stack.item(), stack.count() - accepted));
                    left++;
                }
            }
            xp += Math.max(0L, entry.xp());
            if (!kept.isEmpty()) {
                remaining.add(new CrateEntry(entry.instanceId(), entry.definitionId(), kept, 0L, entry.at()));
            }
        }
        return new ClaimPlan(List.copyOf(remaining), given, left, xp);
    }

    // --- grants ---------------------------------------------------------------------------------------

    /**
     * Stores one instance's grant for {@code player}: in the persisted crate, or in the session crate when
     * {@code sessionOnly} (the player opted out). Returns what fit.
     */
    public synchronized Admission add(UUID player, UUID instanceId, Identifier definitionId,
            List<CrateEntry.Stack> stacks, long xp, long now, boolean sessionOnly, int cap) {
        // The cap covers everything the player has waiting: a session grant counts the persisted crate too.
        int existing = sessionOnly ? sessionStacks(player) + store.get().stackCount(player)
                : store.get().stackCount(player);
        Admission admission = admit(existing, stacks, cap);
        CrateEntry entry = new CrateEntry(instanceId, definitionId, admission.stored(), Math.max(0L, xp), now);
        if (!entry.empty()) {
            if (sessionOnly) {
                sessionCrates.computeIfAbsent(player, p -> new ArrayList<>()).add(entry);
            } else {
                store.get().add(player, entry);
            }
        }
        return admission;
    }

    /** True the first time this session a grant lands in {@code player}'s session crate. */
    public synchronized boolean firstSessionNotice(UUID player) {
        return toldSession.add(player);
    }

    private int sessionStacks(UUID player) {
        int count = 0;
        for (CrateEntry entry : sessionCrates.getOrDefault(player, List.of())) {
            count += entry.items().size();
        }
        return count;
    }

    /** Session-crate entries for {@code player} (counts for the privacy status and export). */
    public synchronized List<CrateEntry> sessionEntries(UUID player) {
        List<CrateEntry> entries = sessionCrates.get(player);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    /** Waiting stacks across the persisted and session crates. */
    public synchronized int waitingStacks(UUID player) {
        return sessionStacks(player) + store.get().stackCount(player);
    }

    /** Erasure: drops the session crate and the session notice flag. */
    public synchronized void forgetSession(UUID player) {
        sessionCrates.remove(player);
        toldSession.remove(player);
    }

    /** Conformance probe for the in-memory session crate. */
    public synchronized boolean holdsSession(UUID player) {
        return sessionCrates.containsKey(player) || toldSession.contains(player);
    }

    // --- claim (the Minecraft adapter) ------------------------------------------------------------------

    /** {@code /neroevents claim}, the console and the companion app: hands the player's crate over. */
    public synchronized ClaimResult claim(@Nullable ServerPlayer player) {
        if (player == null) {
            return new ClaimResult(0, 0, 0L, Status.NOT_ONLINE);
        }
        UUID id = player.getUUID();
        RewardCrateState state = store.get();
        List<CrateEntry> persisted = state.entriesFor(id);
        List<CrateEntry> session = sessionEntries(id);
        if (persisted.isEmpty() && session.isEmpty()) {
            return new ClaimResult(0, 0, 0L, Status.EMPTY);
        }
        InventoryModel inventory = (item, count) -> giveStack(player, item, count);
        ClaimPlan fromStore = apply(persisted, inventory);
        ClaimPlan fromSession = apply(session, inventory);
        state.replace(id, fromStore.remaining());
        if (fromSession.remaining().isEmpty()) {
            sessionCrates.remove(id);
        } else {
            sessionCrates.put(id, new ArrayList<>(fromSession.remaining()));
        }
        long xp = fromStore.xpGiven() + fromSession.xpGiven();
        if (xp > 0L) {
            player.giveExperiencePoints((int) Math.min(Integer.MAX_VALUE, xp));
        }
        int given = fromStore.stacksGiven() + fromSession.stacksGiven();
        int left = fromStore.stacksRemaining() + fromSession.stacksRemaining();
        return new ClaimResult(given, left, xp, left > 0 ? Status.PARTIAL : Status.CLAIMED);
    }

    /** Adds up to {@code count} of {@code item} to the inventory; returns how many went in. */
    private static int giveStack(ServerPlayer player, Identifier itemId, int count) {
        if (count <= 0 || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return 0; // a reward item whose mod was removed waits until it expires
        }
        Item item = BuiltInRegistries.ITEM.getValue(itemId);
        int accepted = 0;
        int left = count;
        while (left > 0) {
            ItemStack stack = new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize()));
            int size = stack.getCount();
            player.getInventory().add(stack);
            int went = size - stack.getCount();
            accepted += went;
            left -= size;
            if (went < size) {
                break;
            }
        }
        return accepted;
    }
}
