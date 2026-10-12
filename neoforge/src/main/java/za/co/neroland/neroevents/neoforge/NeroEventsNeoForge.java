package za.co.neroland.neroevents.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.progression.QuestHooks;

/** NeoForge entry point for NeroEvents. */
@Mod(NeroEventsCommon.MOD_ID)
public final class NeroEventsNeoForge {

    public NeroEventsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroEventsCommon.LOGGER.info("[NeroEvents] NeoForge bootstrap");
        NeroEventsCommon.init();
        // Quest hook delivery (pending hook gates on login) rides the server tick.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> QuestHooks.tick(event.getServer()));
    }
}
