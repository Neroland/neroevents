package za.co.neroland.neroevents.war;

import za.co.neroland.neroevents.compat.factions.FactionsBridge;
import za.co.neroland.neroevents.event.PayloadTypes;

/**
 * Registers {@code neroevents:faction_war}. Called once by {@code FactionsCompat} in common init slot 5, only when
 * NeroFactions is loaded and its bridge is present. The {@code war_beacon} block itself is registered separately
 * ({@link WarBlocks#init()}, slot 3) so it exists whether or not the integration is on.
 */
public final class WarPayloads {

    private WarPayloads() {
    }

    public static synchronized void register(FactionsBridge bridge) {
        if (bridge == null) {
            return;
        }
        FactionWarPayload.bridge(bridge);
        if (PayloadTypes.get(FactionWarPayload.ID).isEmpty()) {
            PayloadTypes.register(new FactionWarPayload());
        }
    }
}
