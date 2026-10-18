package za.co.neroland.neroevents.war;

import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;

/** Test access for erasure conformance: seeds a live war's frozen sides and probes them. */
public final class WarErasureProbe {

    private static final Identifier RED = Identifier.fromNamespaceAndPath("nerofactions", "red");
    private static final Identifier BLUE = Identifier.fromNamespaceAndPath("nerofactions", "blue");

    private WarErasureProbe() {
    }

    /** Registers a live war instance in which every given player has a frozen side. */
    public static void seed(UUID instanceId, UUID... players) {
        FactionWarPayload.State state = new FactionWarPayload.State();
        WarSides sides = new WarSides(RED, BLUE);
        for (UUID player : players) {
            sides.sideOf(player, p -> Set.of(RED));
        }
        state.sides = sides;
        FactionWarPayload.LIVE.put(instanceId, state);
    }

    /** Whether any live war still names the player. */
    public static boolean holds(UUID player) {
        return FactionWarPayload.LIVE.values().stream()
                .anyMatch(s -> s.sides() != null && s.sides().holds(player));
    }

    public static void remove(UUID instanceId) {
        FactionWarPayload.LIVE.remove(instanceId);
    }
}
