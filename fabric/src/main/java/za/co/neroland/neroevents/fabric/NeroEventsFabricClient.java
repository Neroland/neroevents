package za.co.neroland.neroevents.fabric;

import net.fabricmc.api.ClientModInitializer;

import za.co.neroland.neroevents.NeroEventsCommon;

/** Fabric client entry point for NeroEvents. */
public final class NeroEventsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroEventsCommon.LOGGER.info("[NeroEvents] Fabric client bootstrap");
    }
}
