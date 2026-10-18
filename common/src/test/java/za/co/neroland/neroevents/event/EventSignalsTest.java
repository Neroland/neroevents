package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.testing.RecordingPayloadType;

/** The lifecycle bus isolates failing subscribers and coalesces progress. */
class EventSignalsTest {

    @Test
    void throwingSubscriberIsIsolated() {
        try (TestEngine t = new TestEngine()) {
            List<EventSignal> received = new ArrayList<>();
            Consumer<EventSignal> bad = s -> {
                throw new IllegalStateException("bad listener");
            };
            Consumer<EventSignal> good = received::add;
            EventSignals.subscribe(bad);
            EventSignals.subscribe(good);
            try {
                t.start(t.define("isolated", new RecordingPayloadType("isolated"), null));
                t.tick(2);
            } finally {
                EventSignals.unsubscribe(bad);
                EventSignals.unsubscribe(good);
            }
            assertEquals(List.of(EventSignal.Kind.SCHEDULED, EventSignal.Kind.ANNOUNCED),
                    received.stream().map(EventSignal::kind).toList());
            assertEquals(t.kinds(), received.stream().map(EventSignal::kind).toList());
        }
    }

    @Test
    void progressIsCoalescedToOnePerTwentyTicks() {
        try (TestEngine t = new TestEngine()) {
            RecordingPayloadType type = new RecordingPayloadType("progress");
            type.script = n -> TickResult.progress(n, 1000);
            t.start(t.define("progress", type, "{\"announce_ticks\": 0, \"duration_ticks\": 2000}"));
            t.tick(1); // ACTIVE
            t.tick(100);
            long progress = t.signals.stream().filter(s -> s.kind() == EventSignal.Kind.PROGRESS).count();
            assertEquals(5L, progress);
        }
    }

    @Test
    void signalsCarryTheViewOnly() {
        try (TestEngine t = new TestEngine()) {
            EventInstance instance = t.start(t.define("view", new RecordingPayloadType("view"), null));
            EventSignal first = t.signals.get(0);
            assertEquals(instance.id(), first.view().instanceId());
            assertEquals(instance.signals().get(0), first);
        }
    }
}
