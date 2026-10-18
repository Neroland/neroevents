package za.co.neroland.neroevents.participation;

import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.protect.Footprint;

/**
 * The second pure seam beside {@code WorldView}, deliberately separate from it: yes/no answers about
 * players the caller already holds a UUID for (participants and voters). It never returns a position;
 * the only position read happens inside the server implementation and does not leave it.
 */
public interface PresenceView {

    boolean isOnline(UUID player);

    boolean inDimension(UUID player, Identifier dimension);

    /** True iff the player is online, in {@code footprint.dimension()} and {@code footprint.contains(x, z)}. */
    boolean isWithin(UUID player, Footprint footprint);

    /**
     * The online players' ids, for the consent vote only (leavers are dropped from the tally at decision
     * time). Never leaves the engine, never logged.
     */
    Set<UUID> onlineIds();
}
