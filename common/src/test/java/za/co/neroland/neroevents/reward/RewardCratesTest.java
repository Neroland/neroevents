package za.co.neroland.neroevents.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.neroevents.testing.RewardHarness.damage;
import static za.co.neroland.neroevents.testing.RewardHarness.id;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.TestInstances;
import za.co.neroland.neroevents.testing.FakeInventoryModel;
import za.co.neroland.neroevents.testing.RewardHarness;

/** The crate planner, the stack cap, retention and the session crate. */
class RewardCratesTest {

    private static final long DAY = 86_400_000L;
    private static final Identifier INGOT = Identifier.fromNamespaceAndPath("nerolandcore", "nero_alloy_ingot");
    private static final Identifier DUST = Identifier.fromNamespaceAndPath("nerolandcore", "nero_alloy_dust");
    private static final Identifier DEF = Identifier.fromNamespaceAndPath("neroevents", "core/meteor_shower");

    private static CrateEntry entry(long at, long xp, CrateEntry.Stack... stacks) {
        return new CrateEntry(UUID.randomUUID(), DEF, List.of(stacks), xp, at);
    }

    @Test
    void aFullClaimEmptiesTheCrateAndGivesAllXp() {
        RewardCrates.ClaimPlan plan = RewardCrates.apply(List.of(entry(1L, 30L, new CrateEntry.Stack(INGOT, 10)),
                entry(2L, 5L, new CrateEntry.Stack(DUST, 3))), new FakeInventoryModel(100));
        assertTrue(plan.remaining().isEmpty());
        assertEquals(2, plan.stacksGiven());
        assertEquals(0, plan.stacksRemaining());
        assertEquals(35L, plan.xpGiven());
    }

    @Test
    void aPartialClaimKeepsWhatDidNotFitInOrder() {
        CrateEntry first = entry(1L, 0L, new CrateEntry.Stack(INGOT, 10));
        CrateEntry second = entry(2L, 7L, new CrateEntry.Stack(DUST, 3));
        FakeInventoryModel inventory = new FakeInventoryModel(4);
        RewardCrates.ClaimPlan plan = RewardCrates.apply(List.of(first, second), inventory);
        assertEquals(2, plan.remaining().size());
        assertEquals(first.instanceId(), plan.remaining().get(0).instanceId());
        assertEquals(6, plan.remaining().get(0).items().get(0).count());
        assertEquals(3, plan.remaining().get(1).items().get(0).count());
        assertEquals(7L, plan.xpGiven(), "XP is always granted in full");
        assertEquals(0L, plan.remaining().get(1).xp());
    }

    @Test
    void anEmptyInventoryGetsNothingButXp() {
        RewardCrates.ClaimPlan plan = RewardCrates.apply(List.of(entry(1L, 9L, new CrateEntry.Stack(INGOT, 1))),
                new FakeInventoryModel(0));
        assertEquals(0, plan.stacksGiven());
        assertEquals(1, plan.stacksRemaining());
        assertEquals(9L, plan.xpGiven());
    }

    @Test
    void theStackCapMergesFirstThenForfeits() {
        RewardCrates.Admission merged = RewardCrates.admit(0, List.of(new CrateEntry.Stack(INGOT, 40),
                new CrateEntry.Stack(INGOT, 40), new CrateEntry.Stack(DUST, 5)), 27);
        assertEquals(3, merged.stored().size(), "80 ingots → 64 + 16, plus the dust");
        assertEquals(0, merged.forfeitedItems());
        RewardCrates.Admission capped = RewardCrates.admit(26, List.of(new CrateEntry.Stack(INGOT, 3),
                new CrateEntry.Stack(DUST, 5)), 27);
        assertEquals(1, capped.stored().size());
        assertEquals(1, capped.forfeitedStacks());
        assertEquals(5, capped.forfeitedItems());
    }

    @Test
    void expiredEntriesAreForfeitedWhole() {
        RewardCrateState state = new RewardCrateState();
        long now = 100L * DAY;
        state.add(id(1), entry(now - 15L * DAY, 0L, new CrateEntry.Stack(INGOT, 1)));
        state.add(id(1), entry(now - 2L * DAY, 0L, new CrateEntry.Stack(DUST, 1)));
        assertEquals(1, state.purgeExpired(now, 14));
        assertEquals(1, state.entriesFor(id(1)).size());
        assertEquals(1, state.erase(id(1)));
        assertFalse(state.holds(id(1)));
    }

    @Test
    void codecRoundTrip() {
        RewardCrateState state = new RewardCrateState();
        state.add(id(1), entry(5L, 12L, new CrateEntry.Stack(INGOT, 3)));
        JsonElement json = RewardCrateState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        RewardCrateState back = RewardCrateState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(state.entriesFor(id(1)), back.entriesFor(id(1)));
    }

    @Test
    void theSessionCrateIsUsedOnlyForOptedOutPlayersAndClearedByForgetSession() {
        RewardHarness h = new RewardHarness();
        h.optOuts.set(id(2), true);
        EventInstance instance = h.instance(TestInstances.definition("session_crate", "reward",
                "{\"budget\":1000,\"min_share_fraction\":0.0,\"lines\":[{\"kind\":\"item\",\"item\":\"minecraft:diamond\","
                        + "\"count\":4}]}"), EventOutcome.SUCCESS);
        damage(instance, 10, 10);
        h.resolve(instance);
        assertTrue(h.crateState.holds(id(1)));
        assertFalse(h.crateState.holds(id(2)), "nothing persisted for the opted-out player");
        assertEquals(1, h.crates.sessionEntries(id(2)).size());
        assertTrue(h.sinks.told.contains("reward.neroevents.crate_session_only"));
        h.crates.forgetSession(id(2));
        assertFalse(h.crates.holdsSession(id(2)));

        // The cap covers the persisted crate too: one stored stack + two session stacks under cap 2.
        RewardCrateState persisted = new RewardCrateState();
        persisted.add(id(3), entry(1L, 0L, new CrateEntry.Stack(INGOT, 1)));
        RewardCrates capped = new RewardCrates(() -> persisted);
        RewardCrates.Admission admission = capped.add(id(3), UUID.randomUUID(), DEF,
                List.of(new CrateEntry.Stack(INGOT, 1), new CrateEntry.Stack(DUST, 1)), 0L, 2L, true, 2);
        assertEquals(1, admission.stored().size());
        assertEquals(1, admission.forfeitedStacks());
        assertEquals(2, capped.waitingStacks(id(3)));
    }

    @Test
    void aNewEngineStartsWithAnEmptySessionCrate() {
        RewardCrates first = new RewardCrates(RewardCrateState::new);
        first.add(id(1), UUID.randomUUID(), DEF, List.of(new CrateEntry.Stack(INGOT, 1)), 0L, 1L, true, 27);
        assertTrue(first.holdsSession(id(1)));
        RewardCrates next = new RewardCrates(RewardCrateState::new); // what a server change builds
        assertFalse(next.holdsSession(id(1)));
    }
}
