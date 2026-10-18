package za.co.neroland.neroevents.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.data.NeroEventsData;
import za.co.neroland.neroevents.privacy.BackupHook;

/**
 * The short-lived reward ledger {@code neroevents:rewards} (overworld): one row per participant per paid
 * instance, kept {@code privacyRewardLedgerRetentionDays} (7 by default) and at most
 * {@code rewardLedgerMaxEntries} rows. It exists so a reconnecting player is paid once and can see what
 * they earned ({@code /neroevents rewards}).
 *
 * <p>Player-keyed, so it joins the composite eraser as store {@code "reward_ledger"}: erasure removes every
 * row naming the player and refreshes the backup. Rows written while a player had opted out carry no
 * player and are nobody's. Read only through {@link #get(MinecraftServer)} (Core's
 * {@code SavedDataRecovery}). Never logs a UUID.
 */
public final class RewardLedgerState extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "rewards");
    public static final int SCHEMA_VERSION = 1;
    private static final long DAY_MS = 86_400_000L;

    private record Root(int schemaVersion, List<RewardLedgerEntry> rows) {
    }

    /** On disk: {@code { schema_version, rows: [ { player?, instance, definition, summary, at } ] }}. */
    public static final Codec<RewardLedgerState> CODEC = RecordCodecBuilder.<Root>create(inst -> inst.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(Root::schemaVersion),
            RewardLedgerEntry.CODEC.listOf().optionalFieldOf("rows", List.of()).forGetter(Root::rows)
    ).apply(inst, Root::new)).xmap(RewardLedgerState::fromData, RewardLedgerState::toData);

    public static final SavedDataType<RewardLedgerState> TYPE =
            new SavedDataType<>(ID, RewardLedgerState::new, CODEC, null);

    private static final NeroEventsData.StoreEraser ERASER = eraser(RewardLedgerState::get);

    private final List<RewardLedgerEntry> rows = new ArrayList<>();

    public RewardLedgerState() {
    }

    public static RewardLedgerState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, RewardLedgerState::new, ID.toString());
    }

    /** Joins the composite eraser (common init slot 4). Idempotent. */
    public static void registerEraser() {
        NeroEventsData.addStore(ERASER);
    }

    /** A store eraser over the store {@code lookup} returns: drops the subject's rows, then backs up. */
    public static NeroEventsData.StoreEraser eraser(Function<MinecraftServer, RewardLedgerState> lookup) {
        return new NeroEventsData.StoreEraser() {
            @Override
            public String name() {
                return "reward_ledger";
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                lookup.apply(server).erase(player);
            }

            @Override
            public void backupNow(MinecraftServer server) {
                BackupHook.current().backup(server, name(), () -> SavedDataRecovery.backupNow(server.overworld(),
                        TYPE, lookup.apply(server), ID.toString()));
            }
        };
    }

    // --- state ---------------------------------------------------------------------------------------

    /** Whether {@code player} already has a row for {@code instanceId} (reconnect de-duplication). */
    public synchronized boolean hasPaid(UUID player, UUID instanceId) {
        for (RewardLedgerEntry row : rows) {
            if (row.isFor(player) && row.instanceId().equals(instanceId)) {
                return true;
            }
        }
        return false;
    }

    /** Appends a row, evicting the oldest beyond {@code maxEntries}; returns the stored row. */
    public synchronized RewardLedgerEntry record(RewardLedgerEntry row, int maxEntries) {
        rows.add(row);
        while (rows.size() > Math.max(1, maxEntries)) {
            rows.remove(0);
        }
        setDirty();
        return row;
    }

    /** Replaces a stored row's summary with what was actually delivered. */
    public synchronized void amend(RewardLedgerEntry row, RewardSummary summary) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) == row) {
                rows.set(i, row.withSummary(summary));
                setDirty();
                return;
            }
        }
    }

    /** The subject's own rows, oldest first (never anonymous rows, never anyone else's). */
    public synchronized List<RewardLedgerEntry> rowsFor(UUID player) {
        List<RewardLedgerEntry> out = new ArrayList<>();
        for (RewardLedgerEntry row : rows) {
            if (row.isFor(player)) {
                out.add(row);
            }
        }
        return out;
    }

    /** Every row (anonymous ones included), for the aggregate totals. */
    public synchronized List<RewardLedgerEntry> all() {
        return List.copyOf(rows);
    }

    /** Retention: removes rows with {@code at ≤ now − days}; dirties only when something was removed. */
    public synchronized int purgeExpired(long now, int retentionDays) {
        long cutoff = now - retentionDays * DAY_MS;
        int before = rows.size();
        rows.removeIf(row -> row.at() <= cutoff);
        int removed = before - rows.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    /** Erasure: every row naming the player (anonymous rows are nobody's and stay). */
    public synchronized int erase(UUID player) {
        int before = rows.size();
        rows.removeIf(row -> row.isFor(player));
        int removed = before - rows.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    /** Conformance probe. */
    public synchronized boolean holds(UUID player) {
        for (RewardLedgerEntry row : rows) {
            if (row.isFor(player)) {
                return true;
            }
        }
        return false;
    }

    public synchronized int size() {
        return rows.size();
    }

    private synchronized Root toData() {
        return new Root(SCHEMA_VERSION, List.copyOf(rows));
    }

    private static RewardLedgerState fromData(Root root) {
        RewardLedgerState state = new RewardLedgerState();
        state.rows.addAll(root.rows());
        return state;
    }
}
