package za.co.neroland.neroevents.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.neroevents.testing.RewardHarness.damage;
import static za.co.neroland.neroevents.testing.RewardHarness.id;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.TestInstances;
import za.co.neroland.neroevents.testing.RewardHarness;

/** The reward ledger: codec, de-duplication, per-player reads, retention, eviction, erasure, write-ahead. */
class RewardLedgerStateTest {

    private static final long DAY = 86_400_000L;
    private static final Identifier DEF = Identifier.fromNamespaceAndPath("neroevents", "core/supply_drop");
    private static final RewardSummary SUMMARY = new RewardSummary(69L, 46L, 1, 1, 0, false, 2, 5, 231_000L);

    private static RewardLedgerEntry row(Optional<UUID> player, UUID instance, long at) {
        return new RewardLedgerEntry(player, instance, DEF, SUMMARY, at);
    }

    @Test
    void codecRoundTripKeepsAnonymousRows() {
        RewardLedgerState state = new RewardLedgerState();
        UUID instance = UUID.randomUUID();
        state.record(row(Optional.of(id(1)), instance, 10L), 100);
        state.record(row(Optional.empty(), instance, 10L), 100);
        JsonElement json = RewardLedgerState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        RewardLedgerState back = RewardLedgerState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(state.all(), back.all());
        assertTrue(back.all().get(1).anonymous());
    }

    @Test
    void hasPaidAndRowsForAreThePlayersOwn() {
        RewardLedgerState state = new RewardLedgerState();
        UUID instance = UUID.randomUUID();
        state.record(row(Optional.of(id(1)), instance, 10L), 100);
        state.record(row(Optional.of(id(2)), instance, 10L), 100);
        state.record(row(Optional.empty(), instance, 10L), 100);
        assertTrue(state.hasPaid(id(1), instance));
        assertFalse(state.hasPaid(id(3), instance));
        assertEquals(1, state.rowsFor(id(1)).size());
        assertTrue(state.rowsFor(id(1)).stream().allMatch(r -> r.isFor(id(1))));
    }

    @Test
    void purgeRemovesRowsOlderThanTheRetention() {
        RewardLedgerState state = new RewardLedgerState();
        long now = 100L * DAY;
        state.record(row(Optional.of(id(1)), UUID.randomUUID(), now - 8L * DAY), 100);
        state.record(row(Optional.of(id(1)), UUID.randomUUID(), now - 6L * DAY), 100);
        state.setDirty(false);
        assertEquals(1, state.purgeExpired(now, 7));
        assertEquals(1, state.size());
        state.setDirty(false);
        assertEquals(0, state.purgeExpired(now, 7));
        assertFalse(state.isDirty(), "nothing expired ⇒ not dirtied");
    }

    @Test
    void theOldestRowsAreEvictedBeyondTheCap() {
        RewardLedgerState state = new RewardLedgerState();
        List<UUID> instances = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID instance = UUID.randomUUID();
            instances.add(instance);
            state.record(row(Optional.of(id(1)), instance, i), 3);
        }
        assertEquals(3, state.size());
        assertFalse(state.hasPaid(id(1), instances.get(0)));
        assertTrue(state.hasPaid(id(1), instances.get(4)));
    }

    @Test
    void erasureLeavesAnonymousRows() {
        RewardLedgerState state = new RewardLedgerState();
        UUID instance = UUID.randomUUID();
        state.record(row(Optional.of(id(1)), instance, 10L), 100);
        state.record(row(Optional.empty(), instance, 10L), 100);
        state.record(row(Optional.of(id(2)), instance, 10L), 100);
        assertEquals(1, state.erase(id(1)));
        assertFalse(state.holds(id(1)));
        assertTrue(state.holds(id(2)));
        assertEquals(2, state.size());
    }

    @Test
    void theRowIsWrittenBeforeAnySinkRuns() {
        RewardHarness h = new RewardHarness();
        EventInstance instance = h.instance(TestInstances.definition("write_ahead", "reward",
                "{\"budget\":1000,\"lines\":[{\"kind\":\"currency\",\"amount\":100}]}"), EventOutcome.SUCCESS);
        damage(instance, 20);
        List<Boolean> seen = new ArrayList<>();
        h.sinks.beforePay = player -> seen.add(h.ledger.hasPaid(player, instance.id()));
        h.resolve(instance);
        assertEquals(List.of(true), seen);
    }
}
