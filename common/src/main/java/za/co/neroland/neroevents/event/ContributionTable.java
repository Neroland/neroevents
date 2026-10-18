package za.co.neroland.neroevents.event;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import za.co.neroland.neroevents.participation.PresenceView;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.reward.RewardShare;

/**
 * Who did what during one instance, in memory only: credited from attributable actions (damage dealt
 * by, container opened by, objective captured by), never from proximity. Never persisted; a restart
 * empties it. Erased per player through the engine's {@code live_instances} store eraser.
 *
 * <p>The implementation is {@link za.co.neroland.neroevents.participation.WeightedContributionTable}:
 * fixed weights per kind, once-keys, a credit floor and a decay rule for players who leave the bounds or
 * log off. Nothing in it is a position or a wall-clock time.
 */
public interface ContributionTable {

    enum Kind { DAMAGE, LOOT, OBJECTIVE, SURVIVAL }

    /** Credits {@code amount} of {@code kind} (the policy's weight for the kind is applied). */
    void credit(UUID player, Kind kind, double amount);

    /** Credited participants: entries at or above the credit floor. */
    int participantCount();

    /** This player's raw share of the credited total, 0..1; 0 if absent or below the floor. */
    double share(UUID player);

    /** For the reward resolver only — never shown, broadcast or stored. Credited participants only. */
    Set<UUID> participants();

    /** Erasure: forget this player entirely. */
    void forget(UUID player);

    void clear();

    /** Credits once per {@code (player, kind, onceKey)}; {@code false} if that key was already credited. */
    boolean creditOnce(UUID player, Kind kind, String onceKey, double amount);

    /** The decay pass: entries outside the bounds or offline lose weight per inactivity window. */
    void sweep(long now, PresenceView presence, Footprint bounds);

    /** {@code round(weight × 1000)}, 0 if absent. */
    long weightMilli(UUID player);

    /** The credited table sorted by weight (descending) then UUID (ascending), with raw shares. */
    List<RewardShare> ranked();

    /** Per-kind raw totals for one player (for the player's own reward summary only). */
    Map<Kind, Double> breakdown(UUID player);

    /** Whether anything in this table still names the player (erasure conformance probe). */
    boolean holds(UUID player);
}
