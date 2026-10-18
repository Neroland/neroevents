package za.co.neroland.neroevents.war;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.minecraft.resources.Identifier;

/**
 * Which side of one faction war a player fights on — the side rule: resolved <b>once</b> per player per
 * instance, the first time it is asked, and frozen. A player in exactly one of the two warring factions is on that
 * side; a player in both, or in neither, is {@link Side#NEUTRAL}. A membership change mid-war does not move the
 * frozen side (and so never moves already-scored points).
 *
 * <p>In memory only: never saved (a restart re-resolves on first contact), erased per player through
 * {@link #forget}. Pure: the membership lookup is passed in.
 */
public final class WarSides {

    /** A player's side in one war. */
    public enum Side {
        A, B, NEUTRAL;

        /** The other warring side ({@code NEUTRAL} has none). */
        public Side opponent() {
            return this == A ? B : this == B ? A : NEUTRAL;
        }
    }

    private final Identifier factionA;
    private final Identifier factionB;
    private final Map<UUID, Side> frozen = new ConcurrentHashMap<>();

    public WarSides(Identifier factionA, Identifier factionB) {
        this.factionA = factionA;
        this.factionB = factionB;
    }

    public Identifier factionA() {
        return factionA;
    }

    public Identifier factionB() {
        return factionB;
    }

    /** The faction a warring side stands for; empty for {@code NEUTRAL}. */
    public Optional<Identifier> faction(Side side) {
        return side == Side.A ? Optional.of(factionA) : side == Side.B ? Optional.of(factionB) : Optional.empty();
    }

    /** Resolves once and freezes: in exactly one of {A, B} → that side; both or neither → NEUTRAL. */
    public Side sideOf(UUID player, Function<UUID, Set<Identifier>> membership) {
        Side cached = frozen.get(player);
        if (cached != null) {
            return cached;
        }
        Set<Identifier> factions;
        try {
            factions = membership.apply(player);
        } catch (RuntimeException | LinkageError failure) {
            factions = Set.of(); // an unreadable membership is "neither": neutral, never a guess
        }
        Side side = classify(factions == null ? Set.of() : factions);
        Side raced = frozen.putIfAbsent(player, side);
        return raced == null ? side : raced;
    }

    /** The frozen side, if this player was ever resolved in this war. */
    public Optional<Side> frozenSide(UUID player) {
        return Optional.ofNullable(frozen.get(player));
    }

    /** Erasure: forget this player's frozen side. */
    public void forget(UUID player) {
        if (player != null) {
            frozen.remove(player);
        }
    }

    /** Whether anything here still names the player (erasure probe). */
    public boolean holds(UUID player) {
        return player != null && frozen.containsKey(player);
    }

    private Side classify(Set<Identifier> factions) {
        boolean inA = factions.contains(factionA);
        boolean inB = factions.contains(factionB);
        if (inA == inB) {
            return Side.NEUTRAL;
        }
        return inA ? Side.A : Side.B;
    }
}
