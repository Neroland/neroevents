package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.testing.RecordingPayloadType;

/** The payload registry's rules, and a fake façade's type gating the pool. */
class PayloadTypesTest {

    @AfterEach
    void reset() {
        PayloadTypes.resetForTests();
    }

    @Test
    void duplicateIdThrows() {
        PayloadTypes.resetForTests();
        PayloadTypes.register(new RecordingPayloadType("dup"));
        assertThrows(IllegalStateException.class, () -> PayloadTypes.register(new RecordingPayloadType("dup")));
    }

    @Test
    void registerAfterFreezeThrows() {
        PayloadTypes.resetForTests();
        PayloadTypes.freeze();
        assertThrows(IllegalStateException.class, () -> PayloadTypes.register(new RecordingPayloadType("late")));
    }

    /** A sibling façade: registers its type only when its mod is loaded, like common init slot 5. */
    private static void facadeInit(boolean siblingLoaded, RecordingPayloadType type) {
        if (siblingLoaded) {
            PayloadTypes.register(type);
        }
    }

    private static int poolSizeWith(boolean siblingLoaded) {
        PayloadTypes.resetForTests();
        RecordingPayloadType type = new RecordingPayloadType("sibling_raid");
        facadeInit(siblingLoaded, type);
        Identifier id = Identifier.fromNamespaceAndPath("neroevents_test", "sibling_event");
        List<ValidationIssue> issues = new ArrayList<>();
        EventDefinition parsed = EventDefinitions.parse(id, JsonParser.parseString("""
                {"type": "neroevents_test:sibling_raid", "category": "raid", "weight": 5,
                 "display": {"title": "a", "what_to_do": "b", "announce": "c"}}"""), issues).orElseThrow();
        Map<Identifier, EventDefinition> valid = EventDefinitions.validate(Map.of(id, parsed), i -> true, m -> true,
                PayloadTypes::get, issues);
        if (!siblingLoaded) {
            assertTrue(issues.stream().anyMatch(i -> i.dropped() && i.message().startsWith("unknown payload type")));
        }
        return valid.size();
    }

    @Test
    void facadeTypeIsInThePoolExactlyWhenTheSiblingIsLoaded() {
        assertEquals(1, poolSizeWith(true));
        assertEquals(0, poolSizeWith(false));
    }

    @Test
    void theShippedDebugMarkerRegistersThroughInit() {
        PayloadTypes.resetForTests();
        PayloadTypes.register(new DebugMarkerPayload());
        assertTrue(PayloadTypes.ids().contains(DebugMarkerPayload.ID));
    }
}
