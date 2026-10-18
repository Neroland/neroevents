package za.co.neroland.neroevents.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EventCategory;
import za.co.neroland.neroevents.event.EventOrigin;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.testing.FakeWorldView;

/** The aggregate history: codec, cap, the daily budget counter, and nothing that names a player or a place. */
class EventHistoryStateTest {

    private static HistoryEntry entry(EventPhase terminal, long value) {
        return new HistoryEntry(UUID.randomUUID(), Identifier.fromNamespaceAndPath("neroevents", "core/supply_drop"),
                Identifier.fromNamespaceAndPath("neroevents", "supply_drop"), EventCategory.REWARD,
                FakeWorldView.OVERWORLD, EventOrigin.SCHEDULER, 1L, 2L, terminal,
                terminal == EventPhase.DONE ? Optional.of(EventOutcome.SUCCESS) : Optional.empty(),
                terminal == EventPhase.SKIPPED ? Optional.of("no_anchor") : Optional.empty(), 3, value, 40L, 80L, 1, 2, 1);
    }

    @Test
    void codecRoundTripKeepsEntriesBudgetAndTotals() {
        EventHistoryState state = new EventHistoryState();
        long now = Instant.parse("2026-10-17T12:00:00Z").toEpochMilli();
        state.record(entry(EventPhase.DONE, 120L), 200);
        state.record(entry(EventPhase.SKIPPED, 0L), 200);
        state.record(entry(EventPhase.CANCELLED, 0L), 200);
        state.charge(now, 120L);
        JsonElement json = EventHistoryState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        EventHistoryState back = EventHistoryState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(state.latest(10), back.latest(10));
        assertEquals(state.budget(now), back.budget(now));
        assertEquals(new EventHistoryState.Totals(1L, 1L, 1L, 120L), back.totals());
    }

    @Test
    void theCapDropsTheOldest() {
        EventHistoryState state = new EventHistoryState();
        HistoryEntry first = entry(EventPhase.DONE, 0L);
        state.record(first, EventHistoryState.DEFAULT_MAX_ENTRIES);
        for (int i = 0; i < EventHistoryState.DEFAULT_MAX_ENTRIES; i++) {
            state.record(entry(EventPhase.DONE, 0L), EventHistoryState.DEFAULT_MAX_ENTRIES);
        }
        assertEquals(EventHistoryState.DEFAULT_MAX_ENTRIES, state.size());
        assertFalse(state.latest(EventHistoryState.DEFAULT_MAX_ENTRIES).contains(first));
    }

    @Test
    void chargeRollsTheDay() {
        EventHistoryState state = new EventHistoryState();
        long day1 = Instant.parse("2026-10-17T23:00:00Z").toEpochMilli();
        long day2 = Instant.parse("2026-10-18T01:00:00Z").toEpochMilli();
        state.charge(day1, 500L);
        assertEquals(500L, state.budget(day1).spent());
        assertEquals(0L, state.budget(day2).spent());
        state.charge(day2, 20L);
        assertEquals(20L, state.budget(day2).spent());
    }

    @Test
    void anEntryHasNoAnchorAndNoRoster() {
        int uuids = 0;
        for (RecordComponent component : HistoryEntry.class.getRecordComponents()) {
            Class<?> type = component.getType();
            assertFalse(BlockPos.class.isAssignableFrom(type), component.getName());
            if (type == UUID.class) {
                uuids++;
                assertEquals("instanceId", component.getName());
            }
            if (Collection.class.isAssignableFrom(type)) {
                Type generic = component.getGenericType();
                assertFalse(generic instanceof ParameterizedType p && p.getActualTypeArguments()[0] == UUID.class,
                        component.getName());
            }
        }
        assertEquals(1, uuids);
        assertTrue(HistoryEntry.class.isRecord());
    }
}
