package za.co.neroland.neroevents.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.economy.EventsEconomy;
import za.co.neroland.neroevents.economy.MarketShockRequests;
import za.co.neroland.neroevents.network.EventNetwork;

/**
 * Locks the public quest contract: the shipped quest pack and other mods address these ids by string,
 * so a rename here is a silent break for every pack that reads them. Plain JVM, no game bootstrap.
 */
class QuestContractTest {

    @Test
    void modIdIsStable() {
        assertEquals("neroevents", NeroEventsCommon.MOD_ID);
    }

    @Test
    void recordGateIdsAreStable() {
        assertEquals("neroevents:first_participation", EventMilestones.FIRST_PARTICIPATION.toString());
        assertEquals("neroevents:supply_claimed", EventMilestones.SUPPLY_CLAIMED.toString());
        assertEquals("neroevents:raid_repelled", EventMilestones.RAID_REPELLED.toString());
        assertEquals("neroevents:boss_contributor", EventMilestones.BOSS_CONTRIBUTOR.toString());
    }

    @Test
    void hookGateIdIsStable() {
        assertEquals("neroevents:hook/supply_beacon", QuestHooks.SUPPLY_BEACON.toString());
    }

    @Test
    void worldChannelIdsAreStable() {
        assertEquals("neroevents:event_resolved", EventMilestones.EVENT_RESOLVED.toString());
        assertEquals("neroevents:boss_defeated", EventMilestones.BOSS_DEFEATED.toString());
    }

    @Test
    void marketShockRequestChannelIsStable() {
        // NeroEconomy listens on this exact id; a rename silently stops every market shock.
        assertEquals("neroevents:market_shock_request", MarketShockRequests.CHANNEL.toString());
    }

    @Test
    void economyReasonCodesAreStable() {
        // Claimed with NeroEconomy as a closed set and written to its ledger; never rename.
        assertEquals("event_entry", EventsEconomy.REASON_ENTRY);
        assertEquals("event_reward", EventsEconomy.REASON_REWARD);
    }

    @Test
    void resolvedCountMapsToHighestRungAtOrBelow() {
        assertEquals(1L, EventMilestones.rungFor(0L));
        assertEquals(1L, EventMilestones.rungFor(1L));
        assertEquals(1L, EventMilestones.rungFor(4L));
        assertEquals(5L, EventMilestones.rungFor(5L));
        assertEquals(25L, EventMilestones.rungFor(49L));
        assertEquals(1000L, EventMilestones.rungFor(5000L));
    }

    @Test
    void networkChannelIsNeroEventsOwn() {
        assertEquals("main", EventNetwork.CHANNEL);
    }
}
