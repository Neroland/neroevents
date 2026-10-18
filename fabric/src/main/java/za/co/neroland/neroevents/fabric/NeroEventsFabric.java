package za.co.neroland.neroevents.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.command.EventCommands;
import za.co.neroland.neroevents.command.GalleryShowcase;
import za.co.neroland.neroevents.console.ConsoleServer;
import za.co.neroland.neroevents.creatures.EventDamageHooks;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.progression.QuestHooks;

/** Fabric entry point for NeroEvents. */
public final class NeroEventsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroEventsCommon.LOGGER.info("[NeroEvents] Fabric bootstrap");
        // Common init declares the payloads; the network registration below consumes them.
        NeroEventsCommon.init();
        FabricEventNetwork.registerCommon();
        // Quest hook delivery (pending hook gates on login) and the event engine ride the server tick.
        ServerTickEvents.END_SERVER_TICK.register(QuestHooks::tick);
        ServerTickEvents.END_SERVER_TICK.register(EventEngine::tick);
        // The HUD push and the open consoles' refresh, once a second.
        ServerTickEvents.END_SERVER_TICK.register(ConsoleServer::tick);
        // The creative gallery's station loops and demo (no-op without a gallery session); dropped on stop.
        ServerTickEvents.END_SERVER_TICK.register(GalleryShowcase::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(GalleryShowcase::onServerStopping);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EventCommands.register(dispatcher));
        // Event-entity damage (raid waves, the boss): observed after it was applied; one map lookup otherwise.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (!blocked) {
                EventDamageHooks.afterDamage(entity, source, damageTaken);
            }
        });
    }
}
