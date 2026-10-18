package za.co.neroland.neroevents.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.neroevents.testing.RewardHarness.damage;
import static za.co.neroland.neroevents.testing.RewardHarness.id;

import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.TestInstances;
import za.co.neroland.neroevents.reward.RewardCrateState;
import za.co.neroland.neroevents.reward.RewardLedgerState;
import za.co.neroland.neroevents.testing.RewardHarness;

/** Opting out means "no row with my UUID", not "no reward"; a repeat changes nothing. */
class OptOutTest {

    private static EventDefinition def(String path) {
        return TestInstances.definition(path, "reward", "{\"budget\":1000,\"min_share_fraction\":0.0,\"lines\":["
                + "{\"kind\":\"currency\",\"amount\":100},{\"kind\":\"item\",\"item\":\"minecraft:diamond\",\"count\":2}]}");
    }

    @Test
    void anOptedOutGrantLeavesNoRowWithTheUuidAnywhere() {
        RewardHarness h = new RewardHarness();
        h.optOuts.set(id(1), true);
        EventInstance instance = h.instance(def("opted_out"), EventOutcome.SUCCESS);
        damage(instance, 20);
        h.resolve(instance);
        assertEquals(100L, h.sinks.currency.get(id(1)), "still paid");
        assertEquals(1, h.ledger.size());
        assertTrue(h.ledger.all().get(0).anonymous());
        assertEquals(0, h.ledger.all().get(0).summary().rank(), "an anonymous row has no rank");
        assertEquals(0L, h.ledger.all().get(0).summary().sharePpm(), "an anonymous row has no share");
        assertFalse(h.ledger.holds(id(1)));
        assertFalse(h.crateState.holds(id(1)));
        assertEquals(1, h.crates.sessionEntries(id(1)).size());
        String ledgerJson = RewardLedgerState.CODEC.encodeStart(JsonOps.INSTANCE, h.ledger).getOrThrow().toString();
        String crateJson = RewardCrateState.CODEC.encodeStart(JsonOps.INSTANCE, h.crateState).getOrThrow().toString();
        assertFalse(ledgerJson.contains("\"player\""), "the only row is anonymous");
        assertFalse(crateJson.contains("\"player\""), "no crate is stored");
        assertTrue(h.participation.paidThisSession(instance.id(), id(1)), "session de-duplication instead");
    }

    @Test
    void aSecondOptOutChangesNothingAndDoesNotDirtyTheStore() {
        PrivacyOptOuts store = new PrivacyOptOuts();
        assertEquals("privacy.neroevents.optout.done", EventsPrivacyActions.optOutKey(store, id(1)));
        store.setDirty(false);
        assertEquals("privacy.neroevents.optout.already", EventsPrivacyActions.optOutKey(store, id(1)));
        assertFalse(store.isDirty());
        assertEquals(1, store.size());
    }

    @Test
    void optInRestoresStoredHistory() {
        PrivacyOptOuts store = new PrivacyOptOuts();
        EventsPrivacyActions.optOutKey(store, id(1));
        assertEquals("privacy.neroevents.optin.done", EventsPrivacyActions.optInKey(store, id(1)));
        assertEquals("privacy.neroevents.optin.already", EventsPrivacyActions.optInKey(store, id(1)));
        assertFalse(store.isOptedOut(id(1)));
    }

    @Test
    void withTheLedgerOffNobodyHasRowsAndSessionDedupStillWorks() {
        RewardHarness h = new RewardHarness();
        h.cfg.rewardLedgerEnabled = false;
        EventInstance instance = h.instance(def("ledger_off"), EventOutcome.SUCCESS);
        damage(instance, 20, 10);
        h.resolve(instance);
        assertEquals(0, h.ledger.size());
        long paid = h.sinks.currency.get(id(1));
        assertEquals(2, h.resolve(instance).duplicates());
        assertEquals(paid, h.sinks.currency.get(id(1)), "paid once");
    }
}
