package za.co.neroland.neroevents.fabric;

import net.fabricmc.api.ModInitializer;

import za.co.neroland.neroevents.NeroEventsCommon;

/** Fabric entry point for NeroEvents. */
public final class NeroEventsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroEventsCommon.LOGGER.info("[NeroEvents] Fabric bootstrap");
        NeroEventsCommon.init();
    }
}
