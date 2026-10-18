package za.co.neroland.neroevents.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.client.EventsClient;
import za.co.neroland.neroevents.client.GalleryCaptureHarness;
import za.co.neroland.neroevents.client.fx.EventFxClient;
import za.co.neroland.neroevents.client.fx.EventParticle;
import za.co.neroland.neroevents.client.fx.RenderBudget;
import za.co.neroland.neroevents.client.fx.RenderBudgetCommand;
import za.co.neroland.neroevents.client.hud.EventHud;
import za.co.neroland.neroevents.client.render.EventConsoleRenderer;
import za.co.neroland.neroevents.client.screen.EventConsoleScreen;
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

/** Fabric client entry point for NeroEvents. */
public final class NeroEventsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroEventsCommon.LOGGER.info("[NeroEvents] Fabric client bootstrap");
        // Clientbound receivers (client-only API; the main entry point already registered the types).
        FabricEventNetwork.registerClient();
        // The payload entities' renderers (supply pod, meteor, debris): one table in common.
        PayloadEntityRenderers.registerAll(new PayloadEntityRenderers.Sink() {
            @Override
            public <E extends Entity> void register(EntityType<? extends E> type, EntityRendererProvider<E> provider) {
                EntityRendererRegistry.register(type, provider);
            }
        });
        // The war beacon's pylon, emitter ring and ground capture ring.
        BlockEntityRendererRegistry.register(WarBlocks.WAR_BEACON_BLOCK_ENTITY.get(), ctx -> new WarBeaconRenderer());
        // The Event Console: its screen, the severity glyph on its face and the event HUD + its dismiss key.
        MenuScreens.register(ConsoleBlocks.EVENT_CONSOLE_MENU.get(), EventConsoleScreen::new);
        BlockEntityRendererRegistry.register(ConsoleBlocks.EVENT_CONSOLE_BLOCK_ENTITY.get(),
                ctx -> new EventConsoleRenderer());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "event_hud"),
                (graphics, delta) -> EventHud.render(graphics));
        KeyMappingHelper.registerKeyMapping(EventsClient.DISMISS_HUD);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            EventsClient.tick();
            GalleryCaptureHarness.tick();
            // Fabric has no tint registration event here: the console tint goes straight into the colour table.
            EventsClient.registerTintFallback();
        });
        // The four storm particles.
        for (StormVariant variant : StormVariant.values()) {
            ParticleProviderRegistry.getInstance().register(StormParticles.forVariant(variant).get(),
                    sprites -> StormParticle.provider(variant).apply(sprites));
        }
        // The five payload particles, the effects behind them and the client-only /nsevgallery and /nsevrender.
        for (EventParticles.Kind kind : EventParticles.Kind.values()) {
            ParticleProviderRegistry.getInstance().register(EventParticles.forKind(kind).get(),
                    sprites -> EventParticle.provider(kind).apply(sprites));
        }
        EventFxClient.install();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            GalleryCaptureHarness.registerClientCommands(dispatcher);
            RenderBudgetCommand.register(dispatcher);
        });
        // A new session starts from a clean client mirror.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            StormClient.reset();
            EventFxClient.reset();
            RenderBudget.reset();
            EventNetwork.clearClientCaches();
        });
    }
}
