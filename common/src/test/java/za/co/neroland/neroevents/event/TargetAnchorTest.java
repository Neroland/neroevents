package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.RecordingPayloadType;
import za.co.neroland.neroevents.testing.TestSources.DenyInBoxSource;

/** A payload-chosen target anchor obeys the protection contract exactly like every other anchor path. */
class TargetAnchorTest {

    private static final BlockPos TARGET = new BlockPos(5, 64, 5);
    private static final String NO_FALLBACK = "{\"bounds\":{\"kind\":\"target\",\"radius\":16,\"fallback_to_density\":false}}";
    private static final String FALLBACK = "{\"bounds\":{\"kind\":\"target\",\"radius\":16,\"fallback_to_density\":true}}";

    private static RecordingPayloadType targeting(String path) {
        RecordingPayloadType type = new RecordingPayloadType(path);
        type.target = Optional.of(TARGET);
        return type;
    }

    @Test
    void aDeniedTargetIsRefusedForAdminAndSkippedForTheScheduler() {
        try (TestEngine t = new TestEngine(List.of(new DenyInBoxSource("box", 0, 0, 10, 10)))) {
            EventDefinition def = t.define("target_denied", targeting("target_denied"), NO_FALLBACK);
            RequestResult refused = t.engine.request(new EventRequest(def.id(), FakeWorldView.OVERWORLD,
                    EventOrigin.ADMIN, null));
            RequestResult.Refused r = assertInstanceOf(RequestResult.Refused.class, refused);
            assertEquals("no_target", r.code());
            assertEquals("box", r.message());

            t.settings.schedulerEnabled = true;
            t.state.setNextRollAt(0L);
            t.clock.now = 600L;
            t.engine.tickOnce();
            assertTrue(t.engine.live().isEmpty());
            EventInstance skipped = t.engine.recent().get(0);
            assertEquals(EventPhase.SKIPPED, skipped.phase());
            assertEquals("no_target", skipped.reason());
        }
    }

    @Test
    void aDeniedTargetFallsBackToTheDensityPickWhenAllowed() {
        try (TestEngine t = new TestEngine(List.of(new DenyInBoxSource("box", 0, 0, 10, 10)))) {
            EventDefinition def = t.define("target_fallback", targeting("target_fallback"), FALLBACK);
            EventInstance instance = t.start(def);
            assertTrue(instance.phase().live());
            assertNotNull(instance.anchor());
            assertNotEquals(TARGET, instance.anchor());
        }
    }

    @Test
    void anAllowedTargetIsTheAnchor() {
        try (TestEngine t = new TestEngine()) {
            EventDefinition def = t.define("target_ok", targeting("target_ok"), NO_FALLBACK);
            EventInstance instance = t.start(def);
            assertEquals(TARGET, instance.anchor());
        }
    }
}
