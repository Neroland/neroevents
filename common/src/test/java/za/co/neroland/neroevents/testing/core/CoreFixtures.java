package za.co.neroland.neroevents.testing.core;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EnginePayloadContext;
import za.co.neroland.neroevents.event.EventBounds;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOrigin;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.PayloadConfig;
import za.co.neroland.neroevents.event.PayloadType;
import za.co.neroland.neroevents.event.TestInstances;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.protect.ProtectionSource;
import za.co.neroland.neroevents.testing.FakeClock;
import za.co.neroland.neroevents.testing.FakeWorldView;

/** Test fixtures: definitions and instances of the Core-only payload types, and contexts over them. */
public final class CoreFixtures {

    public static final Identifier DIM = FakeWorldView.OVERWORLD;
    public static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    public static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private CoreFixtures() {
    }

    /** A validated definition of {@code type} with {@code config}, {@code duration} ticks, bounds radius {@code radius}. */
    public static EventDefinition definition(PayloadType<?, ?> type, PayloadConfig config, int duration, int radius) {
        EventDefinition base = TestInstances.definition("core_fixture", "hazard", null);
        EventDefinition def = new EventDefinition(base.id(), type.id(), base.category(), base.severity(), base.display(),
                base.weight(), base.cooldownTicks(), base.announceTicks(), duration, true, true, false, false, false,
                base.conditions(), new EventBounds(EventBounds.Kind.RADIUS, radius, true), base.rewards(),
                new JsonObject(), null, null, List.of());
        return def.withPayload(type, config, List.of());
    }

    /** An ACTIVE instance at {@code anchor} in {@link #DIM} with {@code seed}. */
    public static EventInstance instance(EventDefinition def, BlockPos anchor, long seed) {
        return new EventInstance(UUID.randomUUID(), def, DIM, anchor, Footprint.cylinder(DIM, anchor,
                def.bounds().radius(), -64, 319), EventOrigin.ADMIN, seed, 0L, EventPhase.ACTIVE);
    }

    /** A pure context with no protection sources. */
    public static EnginePayloadContext context(EventInstance instance, FakeWorldView world, FakeClock clock) {
        return new EnginePayloadContext(instance, world, new ProtectionQuery(List.of()), clock);
    }

    /** A pure context over the given protection sources. */
    public static EnginePayloadContext context(EventInstance instance, FakeWorldView world, FakeClock clock,
            List<ProtectionSource> sources) {
        return new EnginePayloadContext(instance, world, new ProtectionQuery(sources), clock);
    }
}
