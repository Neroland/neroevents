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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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
        // Quest hook delivery (pending hook gates on login) and the event engine ride the server tick.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> QuestHooks.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> EventEngine.tick(event.getServer()));
        // The HUD push and the open consoles' refresh, once a second.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> ConsoleServer.tick(event.getServer()));
        // The creative gallery's station loops and demo (no-op without a gallery session); dropped on stop.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> GalleryShowcase.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
                GalleryShowcase.onServerStopping(event.getServer()));
        // Running events save their resumable state when the server (or a single-player world) closes.
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> EventEngine.onServerStopping(event.getServer()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> EventCommands.register(event.getDispatcher()));
        // Event-entity damage (raid waves, the boss): observed after it was applied; one map lookup otherwise.
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post e) ->
                EventDamageHooks.afterDamage(e.getEntity(), e.getSource(), e.getInflictedDamage()));
        // Client-only: the payload entities' renderers. Gated so client classes never load on a dedicated server.
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForgeClientSetup.init(modEventBus, modContainer);
        }
    }
}
