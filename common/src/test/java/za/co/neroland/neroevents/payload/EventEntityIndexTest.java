package za.co.neroland.neroevents.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Bind/unbind, clearing one instance, and the new-server identity reset. */
class EventEntityIndexTest {

    private final UUID instanceA = UUID.randomUUID();
    private final UUID instanceB = UUID.randomUUID();

    @BeforeEach
    @AfterEach
    void reset() {
        EventEntityIndex.resetForTests();
    }

    @Test
    void bindLookupUnbind() {
        UUID mob = UUID.randomUUID();
        assertEquals(Optional.empty(), EventEntityIndex.instanceOf(mob));
        EventEntityIndex.bind(mob, instanceA);
        assertEquals(Optional.of(instanceA), EventEntityIndex.instanceOf(mob));
        EventEntityIndex.bind(mob, instanceB);
        assertEquals(Optional.of(instanceB), EventEntityIndex.instanceOf(mob), "a rebind replaces");
        EventEntityIndex.unbind(mob);
        assertEquals(Optional.empty(), EventEntityIndex.instanceOf(mob));
        assertEquals(Optional.empty(), EventEntityIndex.instanceOf(null));
    }

    @Test
    void clearInstanceRemovesOnlyThatInstance() {
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID b1 = UUID.randomUUID();
        EventEntityIndex.bind(a1, instanceA);
        EventEntityIndex.bind(a2, instanceA);
        EventEntityIndex.bind(b1, instanceB);
        EventEntityIndex.clearInstance(instanceA);
        assertEquals(1, EventEntityIndex.size());
        assertEquals(Optional.of(instanceB), EventEntityIndex.instanceOf(b1));
    }

    @Test
    void aNewServerClearsTheMapTheSameServerDoesNot() {
        Object first = new Object();
        Object second = new Object();
        EventEntityIndex.resetForIdentity(first);
        UUID mob = UUID.randomUUID();
        EventEntityIndex.bind(mob, instanceA);
        EventEntityIndex.resetForIdentity(first);
        assertTrue(EventEntityIndex.instanceOf(mob).isPresent(), "same server: kept");
        EventEntityIndex.resetForIdentity(second);
        assertEquals(0, EventEntityIndex.size(), "new server: cleared");
    }
}
