package za.co.neroland.neroevents.forge;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.progression.QuestHooks;

/** MinecraftForge entry point for NeroEvents. */
@Mod(NeroEventsCommon.MOD_ID)
public final class NeroEventsForge {

    public NeroEventsForge(FMLJavaModLoadingContext context) {
        NeroEventsCommon.LOGGER.info("[NeroEvents] Forge bootstrap");
        NeroEventsCommon.init();
        // Quest hook delivery (pending hook gates on login) rides the server tick.
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> QuestHooks.tick(event.server()));
    }
}
