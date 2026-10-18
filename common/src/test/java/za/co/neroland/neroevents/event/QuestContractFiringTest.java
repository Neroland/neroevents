package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.participation.Contributions;
import za.co.neroland.neroevents.testing.FakePayloadContext;
import za.co.neroland.neroevents.testing.RecordingMilestones;
import za.co.neroland.neroevents.testing.RecordingPayloadType;
import za.co.neroland.neroevents.testing.RewardHarness;

/** Where each quest-contract id fires from, with the ids unchanged. */
class QuestContractFiringTest {

    @Test
    void firstParticipationFiresOnTheFirstCreditOnly() {
        try (TestEngine t = new TestEngine()) {
            RecordingMilestones m = new RecordingMilestones();
            t.engine.milestones(m);
            EventInstance instance = t.start(t.define("first", new RecordingPayloadType("first"), null));
            UUID player = UUID.randomUUID();
            instance.contributions().credit(player, ContributionTable.Kind.DAMAGE, 0.5D);
            instance.contributions().credit(player, ContributionTable.Kind.DAMAGE, 3.0D);
            assertEquals(List.of("first_participation"), m.calls);
            assertEquals(Set.of(player), m.firstParticipation);
        }
    }

    @Test
    void supplyClaimedFiresOnTheFirstOpenOnly() {
        try (TestEngine t = new TestEngine()) {
            EventInstance instance = t.start(t.define("supply", new RecordingPayloadType("supply"),
                    "{\"announce_ticks\": 0}"));
            t.tick(1);
            assertEquals(EventPhase.ACTIVE, instance.phase());
            RecordingMilestones m = new RecordingMilestones();
            FakePayloadContext ctx = new FakePayloadContext(instance, t.world, t.clock);
            UUID player = UUID.randomUUID();
            UUID pod = UUID.randomUUID();
            assertTrue(Contributions.creditSupplyClaim(ctx, player, pod, m));
            assertFalse(Contributions.creditSupplyClaim(ctx, player, pod, m));
            assertEquals(List.of("supply_claimed"), m.calls);
            assertEquals(25_000L, instance.contributions().weightMilli(player), "credited as one loot open");
        }
    }

    @Test
    void raidAndBossIdsFireFromTheResolver() {
        RewardHarness raid = new RewardHarness();
        EventInstance r = raid.instance(TestInstances.definition("raid_contract", "raid", null), EventOutcome.SUCCESS);
        RewardHarness.damage(r, 10, 10);
        raid.resolve(r);
        assertEquals(Set.of(RewardHarness.id(1), RewardHarness.id(2)), raid.milestones.raidRepelled);

        RewardHarness boss = new RewardHarness();
        EventDefinition bossDef = TestInstances.definition("boss_contract", "boss", null);
        EventInstance b = boss.instance(bossDef, EventOutcome.SUCCESS);
        RewardHarness.damage(b, 50, 50);
        boss.resolve(b);
        assertEquals(Set.of(RewardHarness.id(1), RewardHarness.id(2)), boss.milestones.bossContributor);
        assertEquals(List.of(bossDef.id()), boss.milestones.bossDefeated, "scope = the boss definition id");
    }

    @Test
    void eventResolvedCarriesTheWorldTotalAcrossDefinitions() {
        try (TestEngine t = new TestEngine()) {
            RecordingMilestones m = new RecordingMilestones();
            t.engine.milestones(m);
            RecordingPayloadType type = new RecordingPayloadType("resolved");
            type.script = n -> TickResult.resolved(EventOutcome.SUCCESS);
            EventDefinition first = t.define("resolved_a", type, "{\"announce_ticks\": 0}");
            EventDefinition second = t.define("resolved_b", type, "{\"announce_ticks\": 0}");
            t.start(first);
            t.tick(5);
            t.start(second);
            t.tick(5);
            assertEquals(List.of(first.id(), second.id()), m.resolvedScopes);
            assertEquals(1L, m.resolvedValues.get(0)[0]);
            assertEquals(2L, m.resolvedValues.get(1)[0]);
        }
    }
}
