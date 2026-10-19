package za.co.neroland.neroevents.privacy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.event.world.SinglePlayer;
import za.co.neroland.neroevents.reward.CrateEntry;
import za.co.neroland.neroevents.reward.RewardConfig;
import za.co.neroland.neroevents.reward.RewardCrateState;
import za.co.neroland.neroevents.reward.RewardLedgerState;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The player's privacy actions — opt out, opt in, status, export — shared by {@code /neroevents privacy}
 * and, later, the event console and the companion app. Every answer is a message for that player only.
 * Opting out (or in) a second time changes nothing and says so. Server thread only.
 */
public final class EventsPrivacyActions {

    private static final ExportCooldown COOLDOWN = new ExportCooldown();

    private EventsPrivacyActions() {
    }

    /** The export rate limit (erased per player by the {@code "exports"} store eraser). */
    public static ExportCooldown cooldown() {
        return COOLDOWN;
    }

    // --- pure decisions (plain-JVM tested) ------------------------------------------------------------------

    /** Records an opt-out; returns the message key ({@code already} when nothing changed). */
    public static String optOutKey(PrivacyOptOuts store, UUID player) {
        return store.set(player, true) ? "privacy.neroevents.optout.done" : "privacy.neroevents.optout.already";
    }

    /** Records an opt-in; returns the message key ({@code already} when nothing changed). */
    public static String optInKey(PrivacyOptOuts store, UUID player) {
        return store.set(player, false) ? "privacy.neroevents.optin.done" : "privacy.neroevents.optin.already";
    }

    /** The single-player wording of a message key: {@code key + ".solo"} in a single-player world, else the key. */
    public static String solo(MinecraftServer server, String key) {
        return SinglePlayer.is(server) ? key + ".solo" : key;
    }

    /** A message key with any single-player suffix removed (for code that switches on the base key). */
    public static String baseKey(String key) {
        return key.endsWith(".solo") ? key.substring(0, key.length() - ".solo".length()) : key;
    }

    // --- actions ------------------------------------------------------------------------------------------

    public static Component optOut(MinecraftServer server, UUID player) {
        RewardConfig cfg = RewardConfig.fromConfig();
        String key = optOutKey(PrivacyOptOuts.get(server), player);
        return Component.translatable(key.endsWith(".done") ? solo(server, key) : key, cfg.ledgerDays());
    }

    public static Component optIn(MinecraftServer server, UUID player) {
        RewardConfig cfg = RewardConfig.fromConfig();
        return Component.translatable(optInKey(PrivacyOptOuts.get(server), player), cfg.ledgerDays());
    }

    public static Component status(MinecraftServer server, UUID player) {
        RewardConfig cfg = RewardConfig.fromConfig();
        // Session and persisted stacks: an opted-out player can still claim what was stored before.
        int waitingStacks = 0;
        EventEngine engine = EventEngine.current();
        if (engine != null && engine.crates().isPresent()) {
            waitingStacks = engine.crates().get().waitingStacks(player);
        }
        if (!cfg.rewardLedgerEnabled) {
            return Component.translatable(solo(server, "privacy.neroevents.status.disabled"));
        }
        if (PrivacyOptOuts.get(server).isOptedOut(player)) {
            return Component.translatable("privacy.neroevents.status.out", waitingStacks);
        }
        int rows = RewardLedgerState.get(server).rowsFor(player).size();
        int stacks = RewardCrateState.get(server).stackCount(player);
        return Component.translatable("privacy.neroevents.status.in", cfg.ledgerDays(), rows, stacks, cfg.crateDays());
    }

    public static Component export(MinecraftServer server, UUID player) {
        RewardConfig cfg = RewardConfig.fromConfig();
        long now = System.currentTimeMillis();
        long wait = COOLDOWN.tryAcquire(player, now, cfg.privacyExportCooldownSeconds * 1000L);
        if (wait > 0L) {
            return Component.translatable("privacy.neroevents.export.cooldown", wait);
        }
        try {
            List<CrateEntry> crate = new ArrayList<>(RewardCrateState.get(server).entriesFor(player));
            PrivacyExport.LiveCounts live = PrivacyExport.LiveCounts.NONE;
            EventEngine engine = EventEngine.current();
            if (engine != null) {
                live = engine.liveCountsFor(player);
                engine.crates().map(c -> c.sessionEntries(player)).ifPresent(crate::addAll);
            }
            boolean optedOut = PrivacyOptOuts.get(server).isOptedOut(player);
            PrivacyExport.Settings settings = new PrivacyExport.Settings(optedOut, cfg.rewardLedgerEnabled,
                    cfg.ledgerDays(), cfg.crateDays());
            String path = PrivacyExport.write(PrivacyExport.directory(server), player,
                    PrivacyExport.build(player, RewardLedgerState.get(server).rowsFor(player), crate, settings, live,
                            now));
            return Component.translatable(solo(server, "privacy.neroevents.export.done"), path);
        } catch (IOException | RuntimeException failure) {
            COOLDOWN.refund(player);
            // The real message holds the UUID-named path: report the exception class only.
            NeroEventsTelemetry.captureHandledException(new IOException(failure.getClass().getName()), "privacy",
                    "export");
            NeroEventsCommon.LOGGER.warn("[NeroEvents] A privacy export failed ({}).", failure.getClass().getName());
            return Component.translatable("privacy.neroevents.export.failed");
        }
    }

}
