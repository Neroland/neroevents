package za.co.neroland.neroevents.event;

import za.co.neroland.neroevents.payload.debris.DebrisCrashPayload;
import za.co.neroland.neroevents.payload.meteor.MeteorShowerPayload;
import za.co.neroland.neroevents.payload.registry.CorePayloadBlockEntities;
import za.co.neroland.neroevents.payload.registry.CorePayloadBlocks;
import za.co.neroland.neroevents.payload.registry.CorePayloadEntities;
import za.co.neroland.neroevents.payload.registry.CorePayloadItems;
import za.co.neroland.neroevents.payload.supply.SupplyDropPayload;
import za.co.neroland.neroevents.protect.ProtectionSources;

/**
 * Common init slot 3: the Core-only payloads' registries (blocks → block entities → entities → items; Fabric
 * registers eagerly, so the order matters), the Core-only payload types ({@code debug_marker},
 * {@code supply_drop}, {@code meteor_shower}, {@code debris_crash}) and the built-in protection sources.
 * Sibling façades register their own types and sources in slot 5, only when the sibling is loaded.
 */
public final class EventPayloads {

    private static boolean initialised;

    private EventPayloads() {
    }

    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        CorePayloadBlocks.init();
        CorePayloadBlockEntities.init();
        CorePayloadEntities.init();
        CorePayloadItems.init();
        PayloadTypes.register(new DebugMarkerPayload());
        PayloadTypes.register(new SupplyDropPayload());
        PayloadTypes.register(new MeteorShowerPayload());
        PayloadTypes.register(new DebrisCrashPayload());
        ProtectionSources.initBuiltIn();
    }
}
