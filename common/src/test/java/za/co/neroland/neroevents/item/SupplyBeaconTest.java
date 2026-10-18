package za.co.neroland.neroevents.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventOrigin;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.EventRequest;
import za.co.neroland.neroevents.event.RequestResult;
import za.co.neroland.neroevents.event.TestInstances;
import za.co.neroland.neroevents.reward.RewardConfig;
import za.co.neroland.neroevents.testing.FakeInventoryModel;
import za.co.neroland.neroevents.testing.FakeWorldView;

/** The Supply Beacon: hand-over never drops, use requests a protected REQUEST, a refusal keeps the beacon. */
class SupplyBeaconTest {

    private static final Identifier DEBUG = Identifier.fromNamespaceAndPath("neroevents", "debug_marker");
    private static final BlockPos HOLDER = new BlockPos(12, 70, -40);

    @Test
    void theDefaultDefinitionIsTheBeaconSupplyDrop() {
        assertEquals("neroevents:core/beacon_supply_drop", RewardConfig.defaults().supplyBeaconDefinition.toString());
    }

    @Test
    void aFullInventoryKeepsTheGateOpen() {
        FakeInventoryModel full = new FakeInventoryModel(0);
        List<String> told = new ArrayList<>();
        boolean delivered = SupplyBeaconDelivery.handOver(() -> full.accept(DEBUG, 1) == 1, () -> told.add("no_room"));
        assertFalse(delivered, "the hook gate stays open for the next login");
        assertEquals(List.of("no_room"), told);
        assertTrue(SupplyBeaconDelivery.handOver(() -> new FakeInventoryModel(1).accept(DEBUG, 1) == 1, () -> {
        }));
    }

    @Test
    void withoutTheDefinitionTheBeaconIsKept() {
        List<EventRequest> requests = new ArrayList<>();
        SupplyBeaconUse.Outcome outcome = SupplyBeaconUse.decide(RewardConfig.DEFAULT_SUPPLY_BEACON_DEFINITION,
                id -> false, r -> {
                    requests.add(r);
                    return new RequestResult.Refused("unknown_definition", null);
                }, FakeWorldView.OVERWORLD, HOLDER);
        assertFalse(outcome.consume());
        assertEquals("item.neroevents.supply_beacon.unavailable", outcome.messageKey());
        assertTrue(requests.isEmpty());
    }

    @Test
    void aQueuedDropConsumesOneBeaconAndTheRequestIsTheHoldersOwn() {
        EventDefinition def = TestInstances.definition("beacon_drop", "reward", null);
        List<EventRequest> requests = new ArrayList<>();
        SupplyBeaconUse.Outcome outcome = SupplyBeaconUse.decide(DEBUG, id -> true, r -> {
            requests.add(r);
            return new RequestResult.Queued(TestInstances.instance(def, EventOutcome.SUCCESS));
        }, FakeWorldView.OVERWORLD, HOLDER);
        assertTrue(outcome.consume());
        assertEquals("item.neroevents.supply_beacon.called", outcome.messageKey());
        assertEquals(1, requests.size());
        assertEquals(EventOrigin.REQUEST, requests.get(0).origin());
        assertEquals(HOLDER, requests.get(0).requestedAnchor());
        assertEquals(DEBUG, requests.get(0).definitionId());
    }

    @Test
    void everyRefusalKeepsTheBeaconWithItsOwnMessage() {
        Map<String, String> expected = Map.of("protected", "protected", "kill_switch", "kill_switch",
                "calm_window", "calm_window", "band_cap", "busy", "type_cap", "busy", "max_concurrent", "busy",
                "no_anchor", "no_anchor", "unknown_definition", "unknown_definition", "disabled", "other");
        expected.forEach((code, key) -> {
            SupplyBeaconUse.Outcome outcome = SupplyBeaconUse.decide(DEBUG, id -> true,
                    r -> new RequestResult.Refused(code, null), FakeWorldView.OVERWORLD, HOLDER);
            assertFalse(outcome.consume(), code);
            assertEquals("item.neroevents.supply_beacon.refused." + key, outcome.messageKey(), code);
        });
    }
}
