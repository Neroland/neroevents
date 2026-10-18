package za.co.neroland.neroevents.creatures;

import za.co.neroland.neroevents.compat.creatures.CreaturesBridge;
import za.co.neroland.neroevents.event.PayloadTypes;

/**
 * Registers the NeroCreatures-backed payload types ({@code pirate_raid}, {@code alien_invasion},
 * {@code boss}). Called once by {@code CreaturesCompat} in common init slot 5, only when NeroCreatures is
 * loaded and its bridge is present.
 */
public final class CreaturePayloads {

    private CreaturePayloads() {
    }

    public static void register(CreaturesBridge bridge) {
        if (bridge == null) {
            return;
        }
        PayloadTypes.register(new PirateRaidPayload(bridge));
        PayloadTypes.register(new AlienInvasionPayload(bridge));
        PayloadTypes.register(new BossPayload(bridge));
    }
}
