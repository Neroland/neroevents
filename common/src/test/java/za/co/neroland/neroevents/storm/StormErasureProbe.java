package za.co.neroland.neroevents.storm;

import java.util.UUID;

/** Test access for erasure conformance: seeds a live storm's exposure map and probes it. */
public final class StormErasureProbe {

    private StormErasureProbe() {
    }

    /** Registers a live storm instance whose exposure map names every given player. */
    public static void seed(UUID instanceId, UUID... players) {
        StormPayloadBase.State state = new StormPayloadBase.State();
        for (UUID player : players) {
            state.exposure.put(player, Boolean.TRUE);
        }
        StormPayloadBase.LIVE.put(instanceId, state);
    }

    /** Whether any live storm still names the player. */
    public static boolean holds(UUID player) {
        return StormPayloadBase.LIVE.values().stream().anyMatch(s -> s.holdsExposure(player));
    }

    public static void remove(UUID instanceId) {
        StormPayloadBase.LIVE.remove(instanceId);
    }
}
