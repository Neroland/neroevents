package za.co.neroland.neroevents.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.damagesource.DamageSource;

/** Queue order, the cap, drain semantics (offline dropped, each credit once) and routing per kind. */
class PendingCreditsTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID POD = UUID.fromString("00000000-0000-0000-0000-0000000000f0");

    /** Records every sink call as a string, in order; players are their UUIDs. */
    private static class RecordingSink implements PendingCredits.Sink<UUID> {
        final List<String> calls = new ArrayList<>();

        @Override
        public boolean loot(UUID player, String onceKey) {
            calls.add("loot " + player + " " + onceKey);
            return true;
        }

        @Override
        public boolean supplyClaim(UUID player, UUID podUuid) {
            calls.add("supplyClaim " + player + " " + podUuid);
            return true;
        }

        @Override
        public boolean objective(UUID player, double fraction) {
            calls.add("objective " + player + " " + fraction);
            return true;
        }

        @Override
        public boolean survival(UUID player, int waveIndex) {
            calls.add("survival " + player + " " + waveIndex);
            return true;
        }

        @Override
        public boolean damage(DamageSource source, float amount) {
            calls.add("damage " + amount);
            return true;
        }
    }

    private static UUID online(UUID id, Set<UUID> online) {
        return online.contains(id) ? id : null;
    }

    @Test
    void drainsInQueueOrder() {
        PendingCredits pending = new PendingCredits();
        pending.loot(ALICE, "crate:1");
        pending.objective(BOB, 0.5D);
        pending.survival(ALICE, 2);
        pending.loot(BOB, "crate:1");
        RecordingSink sink = new RecordingSink();
        assertEquals(4, pending.drainInto(id -> id, sink));
        assertEquals(List.of(
                "loot " + ALICE + " crate:1",
                "objective " + BOB + " 0.5",
                "survival " + ALICE + " 2",
                "loot " + BOB + " crate:1"), sink.calls);
    }

    @Test
    void capDropsFurtherAdds() {
        PendingCredits pending = new PendingCredits();
        for (int i = 0; i < PendingCredits.CAP + 10; i++) {
            pending.loot(ALICE, "pocket:" + i);
        }
        assertEquals(PendingCredits.CAP, pending.size());
        RecordingSink sink = new RecordingSink();
        assertEquals(PendingCredits.CAP, pending.drainInto(id -> id, sink));
        assertEquals("loot " + ALICE + " pocket:0", sink.calls.get(0));
        assertEquals("loot " + ALICE + " pocket:" + (PendingCredits.CAP - 1), sink.calls.get(PendingCredits.CAP - 1));
        pending.loot(ALICE, "pocket:after");
        assertEquals(1, pending.size(), "a drained queue accepts again");
    }

    @Test
    void offlinePlayersAreDroppedAndEachCreditLandsOnce() {
        PendingCredits pending = new PendingCredits();
        pending.loot(ALICE, "pod:x");
        pending.loot(BOB, "pod:x");
        RecordingSink sink = new RecordingSink();
        assertEquals(1, pending.drainInto(id -> online(id, Set.of(ALICE)), sink));
        assertEquals(List.of("loot " + ALICE + " pod:x"), sink.calls);
        assertEquals(0, pending.size());
        assertEquals(0, pending.drainInto(id -> id, sink), "a second drain credits nothing");
        assertEquals(1, sink.calls.size());
    }

    @Test
    void supplyClaimRoutesToTheSupplyClaimCredit() {
        PendingCredits pending = new PendingCredits();
        pending.supplyClaim(ALICE, POD);
        pending.loot(BOB, "pod:" + POD);
        RecordingSink sink = new RecordingSink();
        pending.drainInto(id -> id, sink);
        assertEquals(List.of("supplyClaim " + ALICE + " " + POD, "loot " + BOB + " pod:" + POD), sink.calls);
    }

    @Test
    void nullDamageSourceIsNotQueued() {
        // A real DamageSource needs registry bootstrap (not available on a plain JVM), so only the null guard
        // is tested here; damage routing is a dev-world check.
        PendingCredits pending = new PendingCredits();
        pending.damage(null, 3.0F);
        assertEquals(0, pending.size());
        assertEquals(0, pending.drainInto(id -> id, new RecordingSink()));
    }

    @Test
    void sinkRefusalsAreNotCounted() {
        PendingCredits pending = new PendingCredits();
        pending.loot(ALICE, "a");
        pending.loot(ALICE, "b");
        Map<String, Boolean> answers = Map.of("a", true, "b", false);
        RecordingSink sink = new RecordingSink() {
            @Override
            public boolean loot(UUID player, String onceKey) {
                super.loot(player, onceKey);
                return answers.get(onceKey);
            }
        };
        assertEquals(1, pending.drainInto(id -> id, sink));
        assertEquals(2, sink.calls.size());
    }

    @Test
    void nullsAreIgnoredAtTheDoor() {
        PendingCredits pending = new PendingCredits();
        pending.loot(null, "a");
        pending.loot(ALICE, null);
        pending.supplyClaim(ALICE, null);
        pending.objective(null, 1.0D);
        pending.survival(null, 0);
        assertEquals(0, pending.size());
    }
}
