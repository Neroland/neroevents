package za.co.neroland.neroevents.participation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import za.co.neroland.neroevents.event.ContributionTable.Kind;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.testing.FakePresenceView;
import za.co.neroland.neroevents.testing.FakeWorldView;

/** Weights, once-keys, the credit floor, the survival rule and decay. */
class WeightedContributionTableTest {

    private static final Footprint BOUNDS = Footprint.cylinder(FakeWorldView.OVERWORLD, BlockPos.ZERO, 16, 0, 255);
    private static final int WINDOW = ContributionPolicy.DEFAULT.inactivityTicks();

    private final List<UUID> firsts = new ArrayList<>();
    private final WeightedContributionTable table =
            new WeightedContributionTable(ContributionPolicy.DEFAULT, firsts::add);

    @Test
    void kindWeightsAreApplied() {
        UUID a = UUID.randomUUID();
        table.credit(a, Kind.DAMAGE, 10.0D);
        assertEquals(10_000L, table.weightMilli(a));
        table.credit(a, Kind.LOOT, 1.0D);
        assertEquals(35_000L, table.weightMilli(a));
        table.credit(a, Kind.OBJECTIVE, 0.5D);
        assertEquals(60_000L, table.weightMilli(a));
        table.credit(a, Kind.SURVIVAL, 1.0D);
        assertEquals(70_000L, table.weightMilli(a));
        assertEquals(10.0D, table.breakdown(a).get(Kind.DAMAGE));
    }

    @Test
    void creditOnceIsOncePerKeyAndCapped() {
        UUID a = UUID.randomUUID();
        assertTrue(table.creditOnce(a, Kind.LOOT, "pod:1", 1.0D));
        assertFalse(table.creditOnce(a, Kind.LOOT, "pod:1", 1.0D));
        for (int i = 2; i <= WeightedContributionTable.ONCE_KEY_CAP; i++) {
            assertTrue(table.creditOnce(a, Kind.LOOT, "pod:" + i, 1.0D), "key " + i);
        }
        assertFalse(table.creditOnce(a, Kind.LOOT, "pod:overflow", 1.0D), "the 257th key is refused");
    }

    @Test
    void creditFloorExcludesStrayHits() {
        UUID stray = UUID.randomUUID();
        UUID real = UUID.randomUUID();
        table.credit(stray, Kind.DAMAGE, 0.5D);
        table.credit(real, Kind.DAMAGE, 5.0D);
        assertEquals(1, table.participantCount());
        assertEquals(Set.of(real), table.participants());
        assertEquals(0.0D, table.share(stray));
        assertEquals(1, table.ranked().size());
    }

    @Test
    void survivalNeedsAnotherKindFirst() {
        UUID afk = UUID.randomUUID();
        assertFalse(table.creditOnce(afk, Kind.SURVIVAL, "wave:1", 1.0D));
        table.credit(afk, Kind.SURVIVAL, 1.0D);
        assertEquals(0L, table.weightMilli(afk));
        table.credit(afk, Kind.DAMAGE, 2.0D);
        assertTrue(table.creditOnce(afk, Kind.SURVIVAL, "wave:1", 1.0D));
        assertEquals(12_000L, table.weightMilli(afk));
    }

    @Test
    void insideMeansNoDecay() {
        UUID a = UUID.randomUUID();
        table.credit(a, Kind.DAMAGE, 8.0D);
        FakePresenceView presence = new FakePresenceView().inside(a);
        table.sweep(0L, presence, BOUNDS);
        table.sweep(10L * WINDOW, presence, BOUNDS);
        assertEquals(8_000L, table.weightMilli(a));
    }

    @Test
    void outsideHalvesPerWindowAndDropsBelowTheMinimum() {
        UUID a = UUID.randomUUID();
        table.credit(a, Kind.DAMAGE, 8.0D);
        FakePresenceView presence = new FakePresenceView().outside(a);
        table.sweep(0L, presence, BOUNDS); // the clock starts
        table.sweep(WINDOW, presence, BOUNDS);
        assertEquals(4_000L, table.weightMilli(a), "one window ⇒ × 0.5");
        table.sweep(3L * WINDOW, presence, BOUNDS);
        assertEquals(1_000L, table.weightMilli(a), "three windows ⇒ × 0.125");
        table.sweep(20L * WINDOW, presence, BOUNDS);
        assertEquals(0L, table.weightMilli(a), "below contributionMinWeight ⇒ removed");
        assertFalse(table.participants().contains(a));
    }

    @Test
    void offlineCountsAsOutside() {
        UUID a = UUID.randomUUID();
        table.credit(a, Kind.DAMAGE, 8.0D);
        FakePresenceView presence = new FakePresenceView().offline(a);
        table.sweep(0L, presence, BOUNDS);
        table.sweep(WINDOW, presence, BOUNDS);
        assertEquals(4_000L, table.weightMilli(a));
    }

    @Test
    void reEnteringResetsTheClockNotTheWeight() {
        UUID a = UUID.randomUUID();
        table.credit(a, Kind.DAMAGE, 8.0D);
        FakePresenceView presence = new FakePresenceView().outside(a);
        table.sweep(0L, presence, BOUNDS);
        table.sweep(WINDOW, presence, BOUNDS);
        assertEquals(4_000L, table.weightMilli(a));
        presence.inside(a);
        table.sweep(WINDOW + 1L, presence, BOUNDS);
        assertEquals(4_000L, table.weightMilli(a), "the weight is not restored");
        presence.outside(a);
        table.sweep(WINDOW + 2L, presence, BOUNDS); // a new clock
        table.sweep(2L * WINDOW + 1L, presence, BOUNDS);
        assertEquals(4_000L, table.weightMilli(a), "less than a window since leaving again");
        table.sweep(2L * WINDOW + 2L, presence, BOUNDS);
        assertEquals(2_000L, table.weightMilli(a));
    }

    @Test
    void decayOffMeansNoDecay() {
        WeightedContributionTable steady = new WeightedContributionTable(ContributionPolicy.DEFAULT.withDecay(false),
                ContributionListener.NONE);
        UUID a = UUID.randomUUID();
        steady.credit(a, Kind.DAMAGE, 8.0D);
        FakePresenceView presence = new FakePresenceView().offline(a);
        steady.sweep(0L, presence, BOUNDS);
        steady.sweep(10L * WINDOW, presence, BOUNDS);
        assertEquals(8_000L, steady.weightMilli(a));
    }

    @Test
    void forgetRemovesEverything() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        table.credit(a, Kind.DAMAGE, 8.0D);
        table.credit(b, Kind.DAMAGE, 8.0D);
        table.forget(a);
        assertFalse(table.holds(a));
        assertTrue(table.holds(b));
        assertEquals(0L, table.weightMilli(a));
    }

    @Test
    void firstCreditFiresOncePerPlayerEvenAfterRemovalAndReCredit() {
        UUID a = UUID.randomUUID();
        table.credit(a, Kind.DAMAGE, 1.0D);
        table.credit(a, Kind.DAMAGE, 1.0D);
        FakePresenceView presence = new FakePresenceView().offline(a);
        table.sweep(0L, presence, BOUNDS);
        table.sweep(20L * WINDOW, presence, BOUNDS);
        assertEquals(0L, table.weightMilli(a), "decayed and dropped");
        table.credit(a, Kind.DAMAGE, 3.0D);
        assertEquals(List.of(a), firsts);
    }
}
