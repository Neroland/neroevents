package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.testing.RecordingPayloadType;

/** The "no roster" guarantee, as a test. */
class EventViewTest {

    @Test
    void anchorIsHiddenUnlessLocationPublic() {
        try (TestEngine t = new TestEngine()) {
            EventInstance hidden = t.start(t.define("hidden", new RecordingPayloadType("hidden"), null));
            assertNotNull(hidden.anchor());
            assertNull(hidden.view(t.clock.now).publicAnchor());
            EventInstance shown = t.start(t.define("shown", new RecordingPayloadType("shown"),
                    "{\"location_public\": true}"));
            assertEquals(shown.anchor(), shown.view(t.clock.now).publicAnchor());
        }
    }

    @Test
    void noComponentIsAPlayerIdOrACollection() {
        for (RecordComponent component : EventView.class.getRecordComponents()) {
            Class<?> type = component.getType();
            if (type == UUID.class) {
                assertEquals("instanceId", component.getName(), "only the instance id may be a UUID");
            }
            assertFalse(Collection.class.isAssignableFrom(type), component.getName() + " is a collection");
            assertFalse(Map.class.isAssignableFrom(type), component.getName() + " is a map");
            assertFalse(type.isArray(), component.getName() + " is an array");
        }
    }

    @Test
    void participantsAppearOnlyAsACount() {
        try (TestEngine t = new TestEngine()) {
            EventInstance instance = t.start(t.define("count", new RecordingPayloadType("count"), null));
            instance.contributions().credit(UUID.randomUUID(), ContributionTable.Kind.DAMAGE, 5.0D);
            instance.contributions().credit(UUID.randomUUID(), ContributionTable.Kind.LOOT, 1.0D);
            assertEquals(2, instance.view(t.clock.now).participantCount());
        }
    }
}
