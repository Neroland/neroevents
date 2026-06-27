package za.co.neroland.neroevents.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.neroevents.NeroEventsCommon;

/** NeoForge entry point for NeroEvents. */
@Mod(NeroEventsCommon.MOD_ID)
public final class NeroEventsNeoForge {

    public NeroEventsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroEventsCommon.LOGGER.info("[NeroEvents] NeoForge bootstrap");
        NeroEventsCommon.init();
    }
}
