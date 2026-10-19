package za.co.neroland.neroevents.event.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.event.ConsentMode;
import za.co.neroland.neroevents.testing.FakeWorldView;

/**
 * The single-player rule, and that consent's "always off alone" rule is the same rule. The console snapshot's
 * {@code singlePlayer} flag is {@code SinglePlayer.is(server)}; there is no fake server, so the rule is pinned
 * here on the world view.
 */
class SinglePlayerTest {

    private static FakeWorldView world(boolean dedicated, int online) {
        FakeWorldView world = new FakeWorldView();
        world.dedicated = dedicated;
        if (online > 0) {
            world.online(FakeWorldView.OVERWORLD, online);
        }
        return world;
    }

    @Test
    void integratedWithAtMostOnePlayer() {
        assertEquals(true, SinglePlayer.is(world(false, 0)));
        assertEquals(true, SinglePlayer.is(world(false, 1)));
        assertEquals(false, SinglePlayer.is(world(false, 2)));
        assertEquals(false, SinglePlayer.is(world(true, 0)));
        assertEquals(false, SinglePlayer.is(world(true, 1)));
    }

    @Test
    void consentIsOffExactlyWhenSinglePlayer() {
        for (boolean dedicated : new boolean[] {false, true}) {
            for (int online = 0; online <= 3; online++) {
                boolean single = SinglePlayer.is(world(dedicated, online));
                assertEquals(single, ConsentMode.effective(ConsentMode.VOTE, true, dedicated, online) == ConsentMode.OFF,
                        "dedicated=" + dedicated + " online=" + online);
            }
        }
    }
}
