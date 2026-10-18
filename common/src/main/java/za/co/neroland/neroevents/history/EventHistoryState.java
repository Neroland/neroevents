package za.co.neroland.neroevents.history;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.reward.DailyBudget;

/**
 * The aggregate event history {@code neroevents:history} (overworld): the last {@code historyMaxEntries}
 * events (200 by default), running totals, and today's reward spend against {@code rewardBudgetPerDay}.
 *
 * <p><b>World data only</b> — no player UUID anywhere (the only UUID is each entry's random instance id),
 * so it is not a player-keyed store and has no store eraser; it is still read through Core's
 * {@link SavedDataRecovery}.
 */
public final class EventHistoryState extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "history");
    public static final int SCHEMA_VERSION = 1;
    public static final int DEFAULT_MAX_ENTRIES = 200;

    /** World totals since the store was created. */
    public record Totals(long resolved, long skipped, long cancelled, long rewardsValue) {

        static final Codec<Totals> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.LONG.optionalFieldOf("resolved", 0L).forGetter(Totals::resolved),
                Codec.LONG.optionalFieldOf("skipped", 0L).forGetter(Totals::skipped),
                Codec.LONG.optionalFieldOf("cancelled", 0L).forGetter(Totals::cancelled),
                Codec.LONG.optionalFieldOf("rewards_value", 0L).forGetter(Totals::rewardsValue)
        ).apply(inst, Totals::new));

        static final Totals ZERO = new Totals(0L, 0L, 0L, 0L);
    }

    private record Root(int schemaVersion, List<HistoryEntry> entries, long budgetEpochDay, long budgetSpent,
            Totals totals) {
    }

    public static final Codec<EventHistoryState> CODEC = RecordCodecBuilder.<Root>create(inst -> inst.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(Root::schemaVersion),
            HistoryEntry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(Root::entries),
            Codec.LONG.optionalFieldOf("budget_epoch_day", 0L).forGetter(Root::budgetEpochDay),
            Codec.LONG.optionalFieldOf("budget_spent", 0L).forGetter(Root::budgetSpent),
            Totals.CODEC.optionalFieldOf("totals", Totals.ZERO).forGetter(Root::totals)
    ).apply(inst, Root::new)).xmap(EventHistoryState::fromData, EventHistoryState::toData);

    public static final SavedDataType<EventHistoryState> TYPE =
            new SavedDataType<>(ID, EventHistoryState::new, CODEC, null);

    private final List<HistoryEntry> entries = new ArrayList<>();
    private DailyBudget budget = new DailyBudget(0L, 0L);
    private Totals totals = Totals.ZERO;

    public EventHistoryState() {
    }

    public static EventHistoryState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, EventHistoryState::new, ID.toString());
    }

    /** Appends one entry (newest last), dropping the oldest beyond {@code maxEntries}. */
    public synchronized void record(HistoryEntry entry, int maxEntries) {
        entries.add(entry);
        while (entries.size() > Math.max(1, maxEntries)) {
            entries.remove(0);
        }
        long resolved = totals.resolved();
        long skipped = totals.skipped();
        long cancelled = totals.cancelled();
        if (entry.terminal() == EventPhase.SKIPPED) {
            skipped++;
        } else if (entry.terminal() == EventPhase.CANCELLED) {
            cancelled++;
        } else {
            resolved++;
        }
        totals = new Totals(resolved, skipped, cancelled, totals.rewardsValue() + entry.rewardsTotalValue());
        setDirty();
    }

    /** The newest {@code n} entries, newest first. */
    public synchronized List<HistoryEntry> latest(int n) {
        List<HistoryEntry> out = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0 && out.size() < n; i--) {
            out.add(entries.get(i));
        }
        return out;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized Totals totals() {
        return totals;
    }

    /** Today's (UTC) budget, rolled over to a fresh day when needed. */
    public synchronized DailyBudget budget(long nowEpochMillis) {
        return budget.rolled(nowEpochMillis);
    }

    /** Adds {@code value} to today's spend (rolling the day first). */
    public synchronized void charge(long nowEpochMillis, long value) {
        DailyBudget today = budget.rolled(nowEpochMillis);
        budget = new DailyBudget(today.epochDay(), today.spent() + Math.max(0L, value));
        setDirty();
    }

    private synchronized Root toData() {
        return new Root(SCHEMA_VERSION, List.copyOf(entries), budget.epochDay(), budget.spent(), totals);
    }

    private static EventHistoryState fromData(Root root) {
        EventHistoryState state = new EventHistoryState();
        state.entries.addAll(root.entries());
        state.budget = new DailyBudget(root.budgetEpochDay(), root.budgetSpent());
        state.totals = root.totals();
        return state;
    }
}
