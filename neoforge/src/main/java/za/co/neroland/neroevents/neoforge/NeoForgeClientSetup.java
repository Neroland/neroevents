package za.co.neroland.neroevents.neoforge;

import java.util.List;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.client.EventsClient;
import za.co.neroland.neroevents.client.GalleryCaptureHarness;
import za.co.neroland.neroevents.client.fx.EventFxClient;
import za.co.neroland.neroevents.client.fx.EventParticle;
import za.co.neroland.neroevents.client.fx.RenderBudget;
import za.co.neroland.neroevents.client.fx.RenderBudgetCommand;
import za.co.neroland.neroevents.client.hud.EventHud;
import za.co.neroland.neroevents.client.render.ConsoleTint;
import za.co.neroland.neroevents.client.render.EventConsoleRenderer;
import za.co.neroland.neroevents.client.screen.EventConsoleScreen;
import za.co.neroland.neroevents.client.screen.EventHudConfigScreen;
import za.co.neroland.neroevents.console.ConsoleBlocks;
import za.co.neroland.neroevents.fx.EventParticles;
import za.co.neroland.neroevents.network.EventNetwork;
import za.co.neroland.neroevents.payload.client.PayloadEntityRenderers;
import za.co.neroland.neroevents.storm.StormParticles;
import za.co.neroland.neroevents.storm.StormVariant;
import za.co.neroland.neroevents.storm.client.StormClient;
import za.co.neroland.neroevents.storm.client.StormParticle;
import za.co.neroland.neroevents.war.WarBlocks;
import za.co.neroland.neroevents.war.client.WarBeaconRenderer;

/**
 * NeoForge client-only setup: the payload entities' renderers, the war beacon's and the Event Console's block-entity
 * renderers, the console screen, tint and HUD settings screen (mod list config button), the event HUD and its
 * dismiss key, the storm particle providers and the disconnect reset of the client mirrors. Loaded only on the
 * physical client (the entry point gates on {@code Dist.CLIENT}), so client classes never reach a dedicated server.
 */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(NeoForgeClientSetup::onRegisterEntityRenderers);
        modEventBus.addListener(NeoForgeClientSetup::onRegisterParticleProviders);
        modEventBus.addListener((RegisterMenuScreensEvent event) ->
                event.register(ConsoleBlocks.EVENT_CONSOLE_MENU.get(), EventConsoleScreen::new));
        modEventBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "event_hud"), (g, delta) -> EventHud.render(g)));
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(EventsClient.DISMISS_HUD));
        modEventBus.addListener((RegisterColorHandlersEvent.BlockTintSources event) ->
                event.register(List.of(ConsoleTint.INSTANCE), ConsoleBlocks.EVENT_CONSOLE.get()));
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (container, parent) -> new EventHudConfigScreen(parent));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            EventsClient.tick();
            GalleryCaptureHarness.tick();
        });
        // The payload effects (trails, impact flash, pod beacon) and the client-only /nsevgallery and /nsevrender.
        EventFxClient.install();
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> {
            GalleryCaptureHarness.registerClientCommands(event.getDispatcher());
            RenderBudgetCommand.register(event.getDispatcher());
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            StormClient.reset();
            EventFxClient.reset();
            RenderBudget.reset();
            EventNetwork.clearClientCaches();
        });
    }

    private static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        PayloadEntityRenderers.registerAll(new PayloadEntityRenderers.Sink() {
            @Override
            public <E extends Entity> void register(EntityType<? extends E> type, EntityRendererProvider<E> provider) {
                event.registerEntityRenderer(type, provider);
            }
        });
        event.registerBlockEntityRenderer(WarBlocks.WAR_BEACON_BLOCK_ENTITY.get(), ctx -> new WarBeaconRenderer());
        event.registerBlockEntityRenderer(ConsoleBlocks.EVENT_CONSOLE_BLOCK_ENTITY.get(),
                ctx -> new EventConsoleRenderer());
    }

    private static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        for (StormVariant variant : StormVariant.values()) {
            event.registerSpriteSet(StormParticles.forVariant(variant).get(),
                    sprites -> StormParticle.provider(variant).apply(sprites));
        }
        for (EventParticles.Kind kind : EventParticles.Kind.values()) {
            event.registerSpriteSet(EventParticles.forKind(kind).get(), sprites -> EventParticle.provider(kind).apply(sprites));
        }
    }
}
