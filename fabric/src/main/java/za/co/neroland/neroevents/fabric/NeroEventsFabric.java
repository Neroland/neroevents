package za.co.neroland.neroevents.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.progression.QuestHooks;

/** Fabric entry point for NeroEvents. */
public final class NeroEventsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroEventsCommon.LOGGER.info("[NeroEvents] Fabric bootstrap");
        NeroEventsCommon.init();
        // Quest hook delivery (pending hook gates on login) rides the server tick.
        ServerTickEvents.END_SERVER_TICK.register(QuestHooks::tick);
    }
}
