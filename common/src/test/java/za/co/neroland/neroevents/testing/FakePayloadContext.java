package za.co.neroland.neroevents.testing;

import java.util.List;

import za.co.neroland.neroevents.event.EnginePayloadContext;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.protect.ProtectionQuery;

/** A pure payload context over a fake world, for exercising payload logic directly. */
public final class FakePayloadContext extends EnginePayloadContext {

    public FakePayloadContext(EventInstance instance, FakeWorldView world, FakeClock clock) {
        super(instance, world, new ProtectionQuery(List.of()), clock);
    }
}
