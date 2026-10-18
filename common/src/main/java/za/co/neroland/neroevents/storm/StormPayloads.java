package za.co.neroland.neroevents.storm;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.compat.nerospace.NerospaceBridge;
import za.co.neroland.neroevents.event.PayloadTypes;

/**
 * Registers the storm payload types ({@code solar_storm}, {@code planetary_storm}). Called once by
 * {@code NerospaceCompat} in common init slot 5, only when Nerospace is loaded and its bridge is present,
 * so a server without Nerospace drops the storm definitions loudly at load.
 *
 * <p>No once-flag: the payload-type registry itself is the guard, so a test that resets the registry
 * and re-runs the façades gets the storms registered again.
 */
public final class StormPayloads {

    private StormPayloads() {
    }

    public static synchronized void register(NerospaceBridge bridge) {
        if (bridge == null || PayloadTypes.get(SolarStormPayload.ID).isPresent()) {
            return;
        }
        StormOps ops = new ServerStormOps(bridge);
        PayloadTypes.register(new SolarStormPayload(ops));
        PayloadTypes.register(new PlanetaryStormPayload(ops));
        NeroEventsCommon.LOGGER.info("[NeroEvents] storm payloads registered (solar_storm, planetary_storm)");
    }
}
