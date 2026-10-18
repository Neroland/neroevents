package za.co.neroland.neroevents.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.war.WarSides.Side;

/** The side rule: one side, both ⇒ neutral, neither ⇒ neutral, frozen at first contact, forgettable. */
class WarSidesTest {

    static final Identifier RED = Identifier.fromNamespaceAndPath("nerofactions", "red");
    static final Identifier BLUE = Identifier.fromNamespaceAndPath("nerofactions", "blue");
    static final Identifier GREEN = Identifier.fromNamespaceAndPath("nerofactions", "green");

    private final WarSides sides = new WarSides(RED, BLUE);
    private final Map<UUID, Set<Identifier>> members = new HashMap<>();

    private Side side(UUID player) {
        return sides.sideOf(player, p -> members.getOrDefault(p, Set.of()));
    }

    @Test
    void exactlyOneFactionIsThatSide() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        members.put(a, Set.of(RED, GREEN));
        members.put(b, Set.of(BLUE));
        assertEquals(Side.A, side(a));
        assertEquals(Side.B, side(b));
    }

    @Test
    void bothOrNeitherIsNeutral() {
        UUID both = UUID.randomUUID();
        UUID neither = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        members.put(both, Set.of(RED, BLUE));
        members.put(other, Set.of(GREEN));
        assertEquals(Side.NEUTRAL, side(both));
        assertEquals(Side.NEUTRAL, side(neither));
        assertEquals(Side.NEUTRAL, side(other));
    }

    @Test
    void aMembershipChangeMidWarDoesNotMoveTheFrozenSide() {
        UUID player = UUID.randomUUID();
        members.put(player, Set.of(RED));
        assertEquals(Side.A, side(player));
        members.put(player, Set.of(BLUE));
        assertEquals(Side.A, side(player));
        members.put(player, Set.of(RED, BLUE));
        assertEquals(Side.A, side(player));
    }

    @Test
    void aThrowingLookupIsNeutral() {
        UUID player = UUID.randomUUID();
        assertEquals(Side.NEUTRAL, sides.sideOf(player, p -> {
            throw new LinkageError("api changed");
        }));
    }

    @Test
    void forgetErasesTheFrozenSide() {
        UUID player = UUID.randomUUID();
        members.put(player, Set.of(RED));
        side(player);
        assertTrue(sides.holds(player));
        sides.forget(player);
        assertFalse(sides.holds(player));
        members.put(player, Set.of(BLUE));
        assertEquals(Side.B, side(player), "after erasure the side is resolved afresh");
    }
}
