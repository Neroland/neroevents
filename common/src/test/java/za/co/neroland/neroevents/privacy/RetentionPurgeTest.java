package za.co.neroland.neroevents.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.reward.CrateEntry;
import za.co.neroland.neroevents.reward.RewardConfig;
import za.co.neroland.neroevents.reward.RewardCrateState;
import za.co.neroland.neroevents.reward.RewardLedgerEntry;
import za.co.neroland.neroevents.reward.RewardLedgerState;
import za.co.neroland.neroevents.reward.RewardSummary;

/** Retention defaults, the hard cap, the purge cadence and "dirty only when something went". */
class RetentionPurgeTest {

    private static final long DAY = 86_400_000L;
    private static final Identifier DEF = Identifier.fromNamespaceAndPath("neroevents", "core/supply_drop");
    private static final UUID PLAYER = new UUID(0L, 1L);

    @Test
    void defaultsAreSevenAndFourteenDays() {
        RewardConfig cfg = RewardConfig.defaults();
        assertEquals(7, cfg.ledgerDays());
        assertEquals(14, cfg.crateDays());
        assertFalse(cfg.retentionClamped());
    }

    @Test
    void aConfiguredRetentionAboveTheHardCapIsClampedAndFlagged() {
        RewardConfig cfg = RewardConfig.defaults();
        cfg.privacyRewardLedgerRetentionDays = 120;
        assertEquals(90, cfg.ledgerDays());
        assertTrue(cfg.retentionClamped(), "one warning per server start");
        cfg.privacyRewardLedgerRetentionDays = 7;
        cfg.privacyRetentionHardCapDays = 5;
        assertEquals(5, cfg.ledgerDays(), "the hard cap can only be lowered");
        assertEquals(5, cfg.crateDays());
    }

    @Test
    void thePurgeRunsOnTheFirstPassForANewServerThenEveryInterval() {
        EventsPrivacy.PurgeSchedule schedule = new EventsPrivacy.PurgeSchedule();
        Object server = new Object();
        long interval = 60L * 1200L;
        assertTrue(schedule.due(server, 600L, interval));
        assertTrue(schedule.wasNewServer());
        assertFalse(schedule.due(server, 1200L, interval));
        assertFalse(schedule.due(server, 600L + interval - 1L, interval));
        assertTrue(schedule.due(server, 600L + interval, interval));
        assertFalse(schedule.wasNewServer());
        assertTrue(schedule.due(new Object(), 600L + interval + 1L, interval), "a world load purges at once");
    }

    @Test
    void expiredRowsAndEntriesGoAndNothingIsDirtiedWhenNothingExpired() {
        long now = 100L * DAY;
        RewardLedgerState ledger = new RewardLedgerState();
        RewardCrateState crates = new RewardCrateState();
        ledger.record(new RewardLedgerEntry(Optional.of(PLAYER), UUID.randomUUID(), DEF,
                new RewardSummary(1L, 0L, 0, 0, 0, false, 1, 1, 1_000_000L), now - 8L * DAY), 100);
        crates.add(PLAYER, new CrateEntry(UUID.randomUUID(), DEF,
                List.of(new CrateEntry.Stack(Identifier.fromNamespaceAndPath("minecraft", "diamond"), 1)), 0L,
                now - 15L * DAY));
        EventsPrivacy.Purged purged = EventsPrivacy.purgeAll(ledger, crates, null, now, RewardConfig.defaults());
        assertEquals(new EventsPrivacy.Purged(1, 1, 0), purged);
        assertTrue(purged.any());

        RewardLedgerState fresh = new RewardLedgerState();
        RewardCrateState freshCrates = new RewardCrateState();
        EventsPrivacy.Purged none = EventsPrivacy.purgeAll(fresh, freshCrates, null, now, RewardConfig.defaults());
        assertFalse(none.any());
        assertFalse(fresh.isDirty());
        assertFalse(freshCrates.isDirty());
    }
}
