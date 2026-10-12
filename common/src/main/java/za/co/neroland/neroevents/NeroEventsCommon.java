package za.co.neroland.neroevents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.neroevents.config.NeroEventsConfig;
import za.co.neroland.neroevents.economy.EconomySignals;
import za.co.neroland.neroevents.economy.EventEconomy;
import za.co.neroland.neroevents.platform.Services;
import za.co.neroland.neroevents.progression.QuestHooks;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * Loader-agnostic entry point for NeroEvents. Each loader entry point
 * (Fabric / Forge / NeoForge) calls {@link #init()} once during mod
 * construction. This is a barebones skeleton — no content is registered yet;
 * add shared blocks, items and systems here and reach loader-specific
 * behaviour through a platform seam.
 */
public final class NeroEventsCommon {

    public static final String MOD_ID = "neroevents";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroEvents");

    private NeroEventsCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroEvents] common init");

        // 0. Platform seam, resolved here during construction and never lazily on a tick path — a
        //    late ServiceLoader read can throw ServiceConfigurationError out of gameplay code.
        Services.init();

        // 1. Config first: everything below reads it, including telemetry's opt-out flag.
        NeroEventsConfig.init();

        // 2. Anonymous, NeroEvents-only crash reporting. Must follow the config registration and
        //    precede the rest of init so early failures are still reported. On by default (opt-out via
        //    telemetryEnabled=false); stays inert while the build's DSN is the placeholder
        //    (see NeroEventsTelemetry's PLACEHOLDER_DSN guard).
        NeroEventsTelemetry.init();

        // 3. NeroEconomy's world signals (sector index, market shocks, Reserve, House stock), heard class-free
        //    through Core's ThresholdEvents. Dormant until the event system adds a hook.
        EconomySignals.init();

        // 4. Optional NeroEconomy bridge for paid events and rewards. Touches NeroEconomy only when it is
        //    loaded and new enough; claims must happen here, before the first server starts.
        EventEconomy.init();

        // 5. Quest-pack hook listener (Core progression events only; no quest-mod dependency). Each loader
        //    feeds QuestHooks.tick from the server tick for login delivery of hooks that opened offline.
        QuestHooks.init();
    }
}
