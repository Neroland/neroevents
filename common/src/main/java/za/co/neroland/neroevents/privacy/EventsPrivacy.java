package za.co.neroland.neroevents.privacy;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.data.NeroEventsData;
import za.co.neroland.neroevents.reward.RewardConfig;
import za.co.neroland.neroevents.reward.RewardCrateState;
import za.co.neroland.neroevents.reward.RewardLedgerState;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * Retention for everything player-keyed NeroEvents stores: the reward ledger (7 days by default), the
 * reward crate (14 days, expired entries are forfeited) and export files (7 days), never beyond the
 * {@code privacyRetentionHardCapDays} hard cap. The purge runs on the first engine pass of every world load
 * and then every {@code privacyPurgeIntervalMinutes} of server time, from the engine's scheduler pass (no
 * extra tick hook). One INFO line per purge, only when something was removed, with counts only.
 *
 * <p>Also owns the {@code "exports"} store eraser: an erasure request deletes the subject's export file and
 * forgets their export cooldown.
 */
public final class EventsPrivacy {

    /** The purge cadence: due on the first pass for a new server, then every interval. Plain-JVM tested. */
    public static final class PurgeSchedule {
        private @Nullable Object lastServer;
        private long lastPurgeAt = Long.MIN_VALUE;
        private boolean newServer;

        /** Whether a purge is due for {@code server} at engine time {@code now}; records it when it is. */
        public boolean due(Object server, long now, long intervalTicks) {
            newServer = server != lastServer;
            if (newServer || lastPurgeAt == Long.MIN_VALUE || now - lastPurgeAt >= Math.max(1L, intervalTicks)) {
                lastServer = server;
                lastPurgeAt = now;
                return true;
            }
            return false;
        }

        /** Whether the last due purge was the first for its server (world load). */
        public boolean wasNewServer() {
            return newServer;
        }

        public long lastPurgeAt() {
            return lastPurgeAt;
        }
    }

    /** What one purge removed. */
    public record Purged(int ledgerRows, int crateEntries, int exportFiles) {

        public boolean any() {
            return ledgerRows > 0 || crateEntries > 0 || exportFiles > 0;
        }
    }

    private static final PurgeSchedule SCHEDULE = new PurgeSchedule();
    private static boolean exportEraserRegistered;

    private EventsPrivacy() {
    }

    /** The last purge's engine time (for {@code /neroevents status}), or {@code Long.MIN_VALUE}. */
    public static long lastPurgeAt() {
        return SCHEDULE.lastPurgeAt();
    }

    /** Called from the engine's scheduler pass. Server thread; never throws. */
    public static void tick(MinecraftServer server, long now) {
        try {
            RewardConfig cfg = RewardConfig.fromConfig();
            if (!SCHEDULE.due(server, now, cfg.privacyPurgeIntervalMinutes * 1200L)) {
                return;
            }
            if (SCHEDULE.wasNewServer() && cfg.retentionClamped()) {
                NeroEventsCommon.LOGGER.warn("[NeroEvents] A reward retention setting is above "
                        + "privacyRetentionHardCapDays; using the hard cap.");
            }
            Purged purged = purgeAll(RewardLedgerState.get(server), RewardCrateState.get(server),
                    PrivacyExport.directory(server), System.currentTimeMillis(), cfg);
            if (purged.any()) {
                NeroEventsCommon.LOGGER.info("[NeroEvents] privacy purge: {} ledger row(s), {} crate entr(y/ies), {} "
                        + "export file(s) removed", purged.ledgerRows(), purged.crateEntries(), purged.exportFiles());
            }
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, "privacy", "purge");
        }
    }

    /** One purge over explicit stores (plain-JVM testable). */
    public static Purged purgeAll(RewardLedgerState ledger, RewardCrateState crates, @Nullable Path exports,
            long nowEpochMillis, RewardConfig cfg) {
        int rows = ledger == null ? 0 : ledger.purgeExpired(nowEpochMillis, cfg.ledgerDays());
        int entries = crates == null ? 0 : crates.purgeExpired(nowEpochMillis, cfg.crateDays());
        int files = PrivacyExport.purgeExpired(exports, nowEpochMillis);
        return new Purged(rows, entries, files);
    }

    /** Joins the composite eraser as store {@code "exports"} (common init slot 4). Idempotent. */
    public static synchronized void registerExportEraser() {
        if (exportEraserRegistered) {
            return;
        }
        exportEraserRegistered = true;
        NeroEventsData.addStore(exportEraser(server -> server == null ? null : PrivacyExport.directory(server)));
    }

    /** The export-file eraser over a directory lookup ({@code null} = no file system to touch). */
    public static NeroEventsData.StoreEraser exportEraser(Function<MinecraftServer, Path> directory) {
        return new NeroEventsData.StoreEraser() {
            @Override
            public String name() {
                return "exports";
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                PrivacyExport.delete(directory.apply(server), player);
                EventsPrivacyActions.cooldown().forget(player);
            }

            @Override
            public void backupNow(MinecraftServer server) {
                // A file, not SavedData: nothing to back up.
            }
        };
    }
}
