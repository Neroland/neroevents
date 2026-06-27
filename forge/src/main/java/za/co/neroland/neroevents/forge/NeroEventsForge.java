package za.co.neroland.neroevents.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.neroevents.NeroEventsCommon;

/** MinecraftForge entry point for NeroEvents. */
@Mod(NeroEventsCommon.MOD_ID)
public final class NeroEventsForge {

    public NeroEventsForge(FMLJavaModLoadingContext context) {
        NeroEventsCommon.LOGGER.info("[NeroEvents] Forge bootstrap");
        NeroEventsCommon.init();
    }
}
