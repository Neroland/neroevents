package za.co.neroland.neroevents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.neroevents.compat.CompatRegistry;
import za.co.neroland.neroevents.config.NeroEventsConfig;
import za.co.neroland.neroevents.console.ConsoleBlocks;
import za.co.neroland.neroevents.data.NeroEventsData;
import za.co.neroland.neroevents.economy.EconomySignals;
import za.co.neroland.neroevents.economy.EventsEconomy;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.event.EventPayloads;
import za.co.neroland.neroevents.fx.EventParticles;
import za.co.neroland.neroevents.fx.EventSounds;
import za.co.neroland.neroevents.item.EventItems;
import za.co.neroland.neroevents.item.SupplyBeaconDelivery;
import za.co.neroland.neroevents.network.EventNetwork;
import za.co.neroland.neroevents.payload.debris.WreckDecay;
import za.co.neroland.neroevents.platform.Services;
import za.co.neroland.neroevents.privacy.EventsPrivacy;
import za.co.neroland.neroevents.privacy.PrivacyOptOuts;
import za.co.neroland.neroevents.progression.QuestHooks;
import za.co.neroland.neroevents.reward.RewardCrateState;
import za.co.neroland.neroevents.reward.RewardEvents;
import za.co.neroland.neroevents.reward.RewardLedgerState;
import za.co.neroland.neroevents.storm.StormParticles;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;
import za.co.neroland.neroevents.war.WarBlocks;

/**
 * Loader-agnostic entry point for NeroEvents. Each loader entry point (Fabric / Forge / NeoForge)
 * calls {@link #init()} once during mod construction, then wires its own networking and event hooks.
 *
 * <p>The ordering below is not cosmetic. Fabric registers <em>eagerly</em> — the moment a registry
 * class is touched — so anything that must exist before something else has to be listed before it, on
 * every loader. The numbered steps are the Neroland convention; later work fills the reserved slots
 * rather than inserting itself wherever is convenient.
 */
public final class NeroEventsCommon {

    public static final String MOD_ID = "neroevents";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroEvents");

    private NeroEventsCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroEvents] common init");

        // 0. Platform seams (PlatformInfo + NetworkPlatform), resolved here during construction and
        //    never lazily on a tick path — a late ServiceLoader read can throw
        //    ServiceConfigurationError out of gameplay code (Nerospace crash precedent MC-NEROSPACE-F).
        Services.init();

        // 1. Config first: everything below reads it, including telemetry's opt-out flag.
        NeroEventsConfig.init();

        // 2. Anonymous, NeroEvents-only crash reporting. Must follow the config registration and
        //    precede the rest of init so early failures are still reported. On by default (opt-out via
        //    telemetryEnabled=false); stays inert while the build's DSN is the placeholder
        //    (see NeroEventsTelemetry's PLACEHOLDER_DSN guard).
        NeroEventsTelemetry.init();

        // 3. Content registries. Items first (Fabric registers eagerly): the Supply Beacon. Then the storm
        //    particle types (registered whether or not Nerospace is present), the event engine's Core-only
        //    payload types with their blocks, block entities, entities and items, and the built-in
        //    protection sources (exclusion zones, spawn, station dimension, protected namespaces, security
        //    seam). Then the faction war's beacon block (kept registered without NeroFactions so worlds
        //    keep their blocks), and last the Event Console.
        EventItems.init();
        StormParticles.init();
        //    The payload particles (meteor trails, impact flash, pod beacon ring and reward spark).
        EventParticles.init();
        EventPayloads.init();
        //    Wrecks decay in a sweep after each scheduler pass (no per-block ticker).
        EventEngine.onSchedulerPass(WreckDecay::tick);
        WarBlocks.init();
        //    The Event Console: block, block entity, menu and item (anyone can read it; requests are checked
        //    per player on the server).
        ConsoleBlocks.init();

        // 4. Player-data erasure registration. Registered before any store can exist on purpose:
        //    registering late is how an erasure request silently misses a store (POPIA/GDPR). Every
        //    player-keyed store adds itself to NeroEventsData and reads its SavedData through Core's
        //    SavedDataRecovery.
        NeroEventsData.init();
        //    The player-keyed stores, in this order: reward ledger, reward crates, the reward-history
        //    opt-out set, export files; then the engine's in-memory votes, contributions and session state
        //    through the "live_instances" store eraser.
        RewardLedgerState.registerEraser();
        RewardCrateState.registerEraser();
        PrivacyOptOuts.registerEraser();
        EventsPrivacy.registerExportEraser();
        EventEngine.registerEraser();

        // 5. Compat detection — resolved once here, never per interaction.
        //    5a. NeroEconomy's world signals (sector index, market shocks, Reserve, House stock), heard
        //        class-free through Core's ThresholdEvents. Dormant until the event system adds a hook.
        EconomySignals.init();
        //    5b. Optional NeroEconomy bridge for paid events and rewards. Touches NeroEconomy only when
        //        it is loaded (the manifest floor guarantees the API); claims must happen here,
        //        before the first server starts.
        EventsEconomy.init();
        //    5c. Sibling façades (Nerospace, NeroAgriculture, NeroColonies, Open Parties and Claims,
        //        NeroCreatures, NeroFactions, NeroEconomy's market shock): each registers its payload types or
        //        protection source only when its sibling is loaded and this jar carries the bridge.
        CompatRegistry.init(Services.PLATFORM::isModLoaded);

        // 6. Declare the payloads on NeroEvents' own neroevents:main channel before any loader
        //    registers them: every loader entry point runs this method first, then wires its own
        //    networking (on Forge the channel is sealed at build()).
        EventNetwork.init();

        // 7. Quest-pack hook listener (Core progression events only; no quest-mod dependency). Each
        //    loader feeds QuestHooks.tick from the server tick for login delivery of hooks that opened
        //    offline.
        QuestHooks.init();
        //    The real Supply Beacon hand-over replaces the pending notice.
        QuestHooks.setSupplyBeaconDelivery(SupplyBeaconDelivery.INSTANCE);
        // 7b. The in-memory reward bus (listeners attach later, e.g. the companion app's push).
        RewardEvents.init();
        // 7c. Announcement and vote cues, driven by the engine's lifecycle signals.
        EventSounds.init();

        // 8. Reserved: a NeroLink module, when one exists, goes LAST and swallows its own failures — a
        //    broken companion integration must never take the mod down with it.
    }
}
