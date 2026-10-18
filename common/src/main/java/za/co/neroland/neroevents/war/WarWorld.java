package za.co.neroland.neroevents.war;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Everything {@link FactionWarPayload} needs from the running server, behind one seam so the payload is
 * plain-JVM testable. Production is {@link ServerWarWorld}; tests pass a fake. Server thread only.
 *
 * <p>Privacy: player UUIDs only flow from here to the frozen-side map and the pending credit queue (both in
 * memory); none is logged or saved. Presence answers "who stands at this beacon" — what the event reaches —
 * never who is rewarded.
 */
public interface WarWorld {

    /** Every faction defined on this server; empty when NeroFactions cannot be asked. */
    Set<Identifier> factionIds();

    /** The factions a player belongs to; empty when unknown. */
    Set<Identifier> membershipOf(UUID player);

    /** A faction's display name (faction names are not personal data); empty when unknown. */
    Optional<String> displayName(Identifier faction);

    /** Online, non-spectating players (the eligibility count). */
    List<UUID> onlinePlayers();

    /** The surface Y at a column (the first air above the ground), or empty when that chunk is not loaded. */
    OptionalInt surfaceY(int x, int z);

    /** Whether the block at {@code pos} is air (nothing to replace). */
    boolean isAir(BlockPos pos);

    /** Places a war beacon bound to this instance. Returns whether the beacon now stands there. */
    boolean placeBeacon(BlockPos pos, UUID instanceId, int index, int colourA, int colourB, int captureTicks,
            int captureRadius);

    /** Whether a war beacon bound to {@code instanceId} stands at {@code pos} (an unloaded chunk counts as yes). */
    boolean holdsBeacon(BlockPos pos, UUID instanceId);

    /** Removes the beacon at {@code pos} if it is still a war beacon (idempotent; unloaded chunks are skipped). */
    void removeBeacon(BlockPos pos);

    /** Players (alive, not spectating) within Chebyshev {@code radius} horizontally of {@code pos}. */
    List<UUID> playersNear(BlockPos pos, int radius);

    /** Pushes the objective's visible state to the beacon's block entity (renderer only). */
    void syncBeacon(BlockPos pos, int owner, int progressTicks, boolean contested, int progressSide);

    /** Plays {@code block.war_beacon.captured} at the beacon (a world sound). */
    void playCaptured(BlockPos pos);
}
