package za.co.neroland.neroevents.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.command.EventCommands;
import za.co.neroland.neroevents.command.GalleryShowcase;
import za.co.neroland.neroevents.console.ConsoleServer;
import za.co.neroland.neroevents.creatures.EventDamageHooks;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.progression.QuestHooks;

/**
 * MinecraftForge entry point for NeroEvents.
 *
 * <p>Damage hook: Forge 26.x has a single {@code LivingDamageEvent} (no Pre/Post split), fired just before the
 * final amount is applied, with {@code getAmount()} rather than {@code getNewDamage()}; it is the closest
 * equivalent of NeoForge's {@code LivingDamageEvent.Post} and Fabric's {@code AFTER_DAMAGE}.
 */
@Mod(NeroEventsCommon.MOD_ID)
public final class NeroEventsForge {

    public NeroEventsForge(FMLJavaModLoadingContext context) {
        NeroEventsCommon.LOGGER.info("[NeroEvents] Forge bootstrap");
        // Common init declares the payloads; the channel below is sealed the moment it is built, so
        // that ordering is mandatory on Forge.
        NeroEventsCommon.init();
        // Common init created NeroEvents' deferred registers through Core's registration seam; this
        // attaches them to our mod bus group.
        //? if >=1.21.6 {
        RegistrationProvider.attach(context.getModBusGroup());
        //?} else {
        /*RegistrationProvider.attach(context.getModEventBus());
        *///?}
        ForgeEventNetwork.register();
        //? if >=1.21.9 {
        // Quest hook delivery (pending hook gates on login) and the event engine ride the server tick.
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> QuestHooks.tick(event.server()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> EventEngine.tick(event.server()));
        // The HUD push and the open consoles' refresh, once a second.
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> ConsoleServer.tick(event.server()));
        // The creative gallery's station loops and demo (no-op without a gallery session); dropped on stop.
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> GalleryShowcase.tick(event.server()));
        //?} else if >=1.21.6 {
        /*// Forge 1.21.6 - 1.21.8: the tick event exposes its server through getServer().
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> QuestHooks.tick(event.getServer()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> EventEngine.tick(event.getServer()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> ConsoleServer.tick(event.getServer()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> GalleryShowcase.tick(event.getServer()));
        *///?}
        //? if >=1.21.6 {
        ServerStoppingEvent.BUS.addListener(event -> GalleryShowcase.onServerStopping(event.getServer()));
        // Running events save their resumable state when the server (or a single-player world) closes.
        ServerStoppingEvent.BUS.addListener(event -> EventEngine.onServerStopping(event.getServer()));
        RegisterCommandsEvent.BUS.addListener(event -> EventCommands.register(event.getDispatcher()));
        // Event-entity damage (raid waves, the boss). A statement-bodied lambda keeps the Consumer/Predicate
        // overloads of the cancellable bus unambiguous.
        LivingDamageEvent.BUS.addListener(e -> {
            EventDamageHooks.afterDamage(e.getEntity(), e.getSource(), e.getAmount());
        });
        //?} else {
        /*// Forge before 1.21.6 (EventBus 6): one shared game bus, listeners typed by their lambda parameter.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) -> {
            QuestHooks.tick(event.getServer());
            EventEngine.tick(event.getServer());
            ConsoleServer.tick(event.getServer());
            GalleryShowcase.tick(event.getServer());
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            GalleryShowcase.onServerStopping(event.getServer());
            EventEngine.onServerStopping(event.getServer());
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                EventCommands.register(event.getDispatcher()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((LivingDamageEvent e) ->
                EventDamageHooks.afterDamage(e.getEntity(), e.getSource(), e.getAmount()));
        *///?}
        // Client-only: the payload entities' renderers. Gated so client classes never load on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientSetup.init(context);
        }
    }
}
