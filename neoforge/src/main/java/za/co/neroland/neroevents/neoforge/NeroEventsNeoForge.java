package za.co.neroland.neroevents.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
//? if >=1.20.5 {
import net.neoforged.neoforge.event.tick.ServerTickEvent;
//?}

import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.command.EventCommands;
import za.co.neroland.neroevents.command.GalleryShowcase;
import za.co.neroland.neroevents.console.ConsoleServer;
import za.co.neroland.neroevents.creatures.EventDamageHooks;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.progression.QuestHooks;

/** NeoForge entry point for NeroEvents. */
@Mod(NeroEventsCommon.MOD_ID)
public final class NeroEventsNeoForge {

    public NeroEventsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroEventsCommon.LOGGER.info("[NeroEvents] NeoForge bootstrap");
        // Common init declares the payloads; the registrar listener below consumes them.
        NeroEventsCommon.init();
        // Common init created NeroEvents' deferred registers through Core's registration seam; this
        // attaches them to our mod event bus.
        RegistrationProvider.attach(modEventBus);
        NeoForgeEventNetwork.register(modEventBus);
        //? if >=1.20.5 {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        //?} else {
        /*NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.TickEvent.ServerTickEvent event) -> {
            if (event.phase == net.neoforged.neoforge.event.TickEvent.Phase.END) {
                tick(event.getServer());
            }
        });
        *///?}
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
                GalleryShowcase.onServerStopping(event.getServer()));
        // Running events save their resumable state when the server (or a single-player world) closes.
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> EventEngine.onServerStopping(event.getServer()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> EventCommands.register(event.getDispatcher()));
        // Event-entity damage (raid waves, the boss): observed after it was applied; one map lookup otherwise.
        //? if >=26.1 {
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post e) ->
                EventDamageHooks.afterDamage(e.getEntity(), e.getSource(), e.getInflictedDamage()));
        //?} else if >=1.21 {
        /*NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post e) ->
                EventDamageHooks.afterDamage(e.getEntity(), e.getSource(), e.getNewDamage()));
        *///?} else {
        /*NeoForge.EVENT_BUS.addListener((LivingDamageEvent e) ->
                EventDamageHooks.afterDamage(e.getEntity(), e.getSource(), e.getAmount()));
        *///?}
        // Client-only: the payload entities' renderers. Gated so client classes never load on a dedicated server.
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForgeClientSetup.init(modEventBus, modContainer);
        }
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        QuestHooks.tick(server);
        EventEngine.tick(server);
        ConsoleServer.tick(server);
        GalleryShowcase.tick(server);
    }
}
