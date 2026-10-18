package za.co.neroland.neroevents.forge;

import java.util.List;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

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
 * Forge client-only setup: the payload entities' renderers, the war beacon's and the Event Console's block-entity
 * renderers, the console screen, tint and HUD settings screen (mod list config button), the event HUD and its
 * dismiss key, the storm particle providers and the disconnect reset of the client mirrors. Loaded only on the
 * physical client (the entry point gates on {@code Dist.CLIENT}), so client classes never reach a dedicated server.
 */
public final class ForgeClientSetup {

    private ForgeClientSetup() {
    }

    /** Forge 26.x: each event class owns a static {@code BUS}; the client-setup event lives on the mod bus group. */
    public static void init(FMLJavaModLoadingContext context) {
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(ForgeClientSetup::onClientSetup);
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(ForgeClientSetup::onRegisterEntityRenderers);
        RegisterParticleProvidersEvent.BUS.addListener(ForgeClientSetup::onRegisterParticleProviders);
        AddGuiOverlayLayersEvent.BUS.addListener(event -> event.getLayeredDraw().add(
                Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "event_hud"), (g, delta) -> EventHud.render(g)));
        RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(EventsClient.DISMISS_HUD));
        RegisterColorHandlersEvent.Block.BUS.addListener(event ->
                event.register(List.of(ConsoleTint.INSTANCE), ConsoleBlocks.EVENT_CONSOLE.get()));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            EventsClient.tick();
            GalleryCaptureHarness.tick();
        });
        // The payload effects (trails, impact flash, pod beacon) and the client-only /nsevgallery and /nsevrender.
        EventFxClient.install();
        RegisterClientCommandsEvent.BUS.addListener(event -> {
            GalleryCaptureHarness.registerClientCommands(event.getDispatcher());
            RenderBudgetCommand.register(event.getDispatcher());
        });
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new EventHudConfigScreen(parent)));
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> {
            StormClient.reset();
            EventFxClient.reset();
            RenderBudget.reset();
            EventNetwork.clearClientCaches();
        });
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ConsoleBlocks.EVENT_CONSOLE_MENU.get(), EventConsoleScreen::new));
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
