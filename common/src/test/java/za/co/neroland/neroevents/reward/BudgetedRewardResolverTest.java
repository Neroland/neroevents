package za.co.neroland.neroevents.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.neroevents.testing.RewardHarness.damage;
import static za.co.neroland.neroevents.testing.RewardHarness.id;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.TestInstances;
import za.co.neroland.neroevents.testing.RewardHarness;

/** The budgeted resolver: pool clamp, delivery paths, the quest contract and failure isolation. */
class BudgetedRewardResolverTest {

    private static final String LINES = "[{\"kind\":\"currency\",\"amount\":100},{\"kind\":\"xp\",\"amount\":200},"
            + "{\"kind\":\"item\",\"item\":\"minecraft:diamond\",\"count\":4}]"; // 100 + 50 + 40 = 190 VU

    private static EventDefinition def(String path, String category, long budget, String extra) {
        return TestInstances.definition(path, category, "{\"budget\":" + budget + ",\"min_share_fraction\":0.0,"
                + "\"lines\":" + LINES + (extra == null ? "" : "," + extra) + "}");
    }

    private static long total(java.util.Map<UUID, Long> map) {
        return map.values().stream().mapToLong(Long::longValue).sum();
    }

    private static int stacks(RewardHarness h, int players) {
        int n = 0;
        for (int i = 1; i <= players; i++) {
            n += h.crateState.entriesFor(id(i)).stream().mapToInt(e -> e.itemCount()).sum();
        }
        return n;
    }

    @Test
    void theDefinitionBudgetBindsAndScalesEveryLineAlike() {
        RewardHarness h = new RewardHarness();
        h.sinks.online.add(id(1));
        h.sinks.online.add(id(2));
        EventInstance instance = h.instance(def("budget_binds", "reward", 95, null), EventOutcome.SUCCESS);
        damage(instance, 30, 10);
        RewardOutcome out = h.resolve(instance);
        assertEquals(95L, out.pool());
        assertEquals(500_000L, out.factorPpm());
        assertEquals(50L, total(h.sinks.currency));
        assertEquals(100L, total(h.sinks.xp));
        assertEquals(2, stacks(h, 2));
    }

    @Test
    void theLinesBindWhenTheyAreWorthLessThanTheBudget() {
        RewardHarness h = new RewardHarness();
        EventInstance instance = h.instance(def("lines_bind", "reward", 1000, null), EventOutcome.SUCCESS);
        damage(instance, 30, 10);
        RewardOutcome out = h.resolve(instance);
        assertEquals(190L, out.pool());
        assertEquals(RewardSplit.SCALE, out.factorPpm());
        assertEquals(100L, total(h.sinks.currency));
        assertEquals(4, stacks(h, 2));
        assertEquals(190L, h.history.budget(h.clock.millis).spent(), "everything paid is charged");
    }

    @Test
    void todaysRemainingBudgetBinds() {
        RewardHarness h = new RewardHarness();
        h.history.charge(h.clock.millis, h.cfg.rewardBudgetPerDay - 38L);
        EventInstance instance = h.instance(def("remaining_binds", "reward", 1000, null), EventOutcome.SUCCESS);
        damage(instance, 30, 10);
        RewardOutcome out = h.resolve(instance);
        assertEquals(38L, out.pool());
        assertEquals(20L, total(h.sinks.currency));
        assertEquals(0, stacks(h, 2), "4 × 0.2 rounds down to no items");
    }

    @Test
    void anExhaustedBudgetPaysNothingAndSaysSoOnce() {
        RewardHarness h = new RewardHarness();
        h.history.charge(h.clock.millis, h.cfg.rewardBudgetPerDay);
        EventInstance instance = h.instance(def("exhausted", "reward", 1000, null), EventOutcome.SUCCESS);
        damage(instance, 30, 10);
        RewardOutcome out = h.resolve(instance);
        assertTrue(out.budgetExhausted());
        assertTrue(h.sinks.currency.isEmpty());
        assertEquals(List.of("announce.neroevents.budget_exhausted"), h.sinks.broadcasts);
        assertEquals(0, h.ledger.size());

        // A cap of 0 is "no rewards at all": it pays nothing and says nothing.
        RewardHarness off = new RewardHarness();
        off.cfg.rewardBudgetPerDay = 0L;
        EventInstance quiet = off.instance(def("budget_off", "reward", 1000, null), EventOutcome.SUCCESS);
        damage(quiet, 30, 10);
        RewardOutcome none = off.resolve(quiet);
        assertTrue(none.budgetExhausted());
        assertTrue(off.sinks.currency.isEmpty());
        assertTrue(off.sinks.broadcasts.isEmpty(), "no budget note when the cap is 0");
    }

    @Test
    void aBossChargesItsOwnDropsFirstAndRecordsContributors() {
        RewardHarness h = new RewardHarness();
        EventDefinition boss = TestInstances.definition("boss", "boss",
                "{\"budget\":1000,\"min_share_fraction\":0.0,\"lines\":[{\"kind\":\"currency\",\"amount\":100}]}");
        EventInstance instance = h.instance(boss, EventOutcome.SUCCESS);
        damage(instance, 96, 4);
        RewardOutcome out = h.resolve(instance);
        assertFalse(out.escaped());
        assertEquals(List.of(id(1)), List.copyOf(h.milestones.bossContributor), "4 % is below bossContributorMinShare");
        assertEquals(List.of(boss.id()), h.milestones.bossDefeated);
        long external = h.cfg.bossCreaturesRewardValuePerParticipant * 2;
        assertEquals(external + 100L, h.history.budget(h.clock.millis).spent());
        assertEquals(external + 100L, out.chargedValue());
    }

    @Test
    void aBossNobodyFoughtEscapes() {
        RewardHarness h = new RewardHarness();
        EventDefinition boss = TestInstances.definition("boss_escape", "boss",
                "{\"budget\":1000,\"lines\":[{\"kind\":\"currency\",\"amount\":100}]}");
        RewardOutcome out = h.resolve(h.instance(boss, EventOutcome.SUCCESS));
        assertTrue(out.escaped());
        assertTrue(h.milestones.bossDefeated.isEmpty());
        assertEquals(0L, h.history.budget(h.clock.millis).spent());
    }

    @Test
    void aRaidSuccessRecordsEveryCreditedParticipantAndNobodyElse() {
        RewardHarness h = new RewardHarness();
        EventInstance instance = h.instance(def("raid", "raid", 1000, null), EventOutcome.SUCCESS);
        damage(instance, 20, 10, 0.5);
        h.resolve(instance);
        assertEquals(java.util.Set.of(id(1), id(2)), h.milestones.raidRepelled);
    }

    @Test
    void failureAndEscapePayNothingButParticipationStillCounts() {
        RewardHarness h = new RewardHarness();
        EventInstance failed = h.instance(def("raid_failed", "raid", 1000, null), EventOutcome.FAILURE);
        damage(failed, 20);
        h.resolve(failed);
        assertTrue(h.sinks.currency.isEmpty());
        assertTrue(h.milestones.raidRepelled.isEmpty());
        assertEquals(java.util.Set.of(id(1)), h.milestones.firstParticipation);
        EventInstance escaped = h.instance(def("escaped", "reward", 1000, null), EventOutcome.ESCAPED);
        damage(escaped, 20);
        h.resolve(escaped);
        assertTrue(h.sinks.currency.isEmpty());
    }

    @Test
    void payOnExpiryIsHonoured() {
        RewardHarness h = new RewardHarness();
        EventInstance plain = h.instance(def("expired_plain", "reward", 1000, null), EventOutcome.EXPIRED);
        damage(plain, 20);
        h.resolve(plain);
        assertTrue(h.sinks.currency.isEmpty());
        EventInstance paid = h.instance(def("expired_paid", "reward", 1000, "\"pay_on_expiry\":true"),
                EventOutcome.EXPIRED);
        damage(paid, 20);
        h.resolve(paid);
        assertEquals(100L, total(h.sinks.currency));
    }

    @Test
    void withoutNeroEconomyCurrencyIsSkippedAndTheRestIsStillGranted() {
        RewardHarness h = new RewardHarness();
        h.sinks.economy = false;
        h.sinks.online.add(id(1));
        EventInstance instance = h.instance(def("core_only", "reward", 1000, null), EventOutcome.SUCCESS);
        damage(instance, 20);
        RewardOutcome out = h.resolve(instance);
        assertTrue(out.currencySkipped());
        assertTrue(h.sinks.currency.isEmpty());
        assertEquals(200L, total(h.sinks.xp));
        assertEquals(4, stacks(h, 1));
        assertTrue(h.ledger.rowsFor(id(1)).get(0).summary().currencySkipped());
    }

    @Test
    void aRefusalIsCountedAndTheLedgerRecordsOnlyWhatWasApplied() {
        RewardHarness h = new RewardHarness();
        EventInstance instance = h.instance(def("refused", "reward", 1000, null), EventOutcome.SUCCESS);
        damage(instance, 30, 10);
        h.sinks.refuseKeys.add("ev:" + instance.shortId() + ":0:1");
        RewardOutcome out = h.resolve(instance);
        assertEquals(1, out.currencyRefused());
        assertEquals(0L, h.ledger.rowsFor(id(1)).get(0).summary().currencyPaid());
        assertTrue(h.ledger.rowsFor(id(2)).get(0).summary().currencyPaid() > 0L);
        assertTrue(h.sinks.told.contains("reward.neroevents.currency_refused"));
    }

    @Test
    void anOfflineParticipantIsPaidByUuidAndTheirXpWaitsInTheCrate() {
        RewardHarness h = new RewardHarness();
        EventInstance instance = h.instance(def("offline", "reward", 1000, null), EventOutcome.SUCCESS);
        damage(instance, 20);
        h.resolve(instance);
        assertEquals(100L, h.sinks.currency.get(id(1)));
        assertTrue(h.sinks.xp.isEmpty());
        assertEquals(200L, h.crateState.entriesFor(id(1)).get(0).xp());
        assertEquals(200L, h.ledger.rowsFor(id(1)).get(0).summary().xpGranted());
    }

    @Test
    void reputationNeedsARealProviderAndIsCapped() {
        String rep = "{\"budget\":1000,\"lines\":[{\"kind\":\"reputation\",\"faction\":\"nerofactions:guild\",\"delta\":100}]}";
        RewardHarness none = new RewardHarness();
        EventInstance a = none.instance(TestInstances.definition("rep_none", "reward", rep), EventOutcome.SUCCESS);
        damage(a, 20);
        none.resolve(a);
        assertTrue(none.sinks.reputationDeltas.isEmpty());

        RewardHarness real = new RewardHarness();
        real.sinks.reputation = true;
        EventInstance b = real.instance(TestInstances.definition("rep_real", "reward", rep), EventOutcome.SUCCESS);
        damage(b, 20);
        real.resolve(b);
        assertEquals(25, real.sinks.reputationDeltas.get(id(1)), "capped at rewardReputationMaxDeltaPerEvent");
    }

    @Test
    void aTopBonusGoesToThatRankOnlyAndIsSkippedWhenNobodyHoldsIt() {
        RewardHarness h = new RewardHarness();
        EventDefinition def = TestInstances.definition("bonus", "reward", "{\"budget\":10000,\"min_share_fraction\":0.0,"
                + "\"lines\":[{\"kind\":\"currency\",\"amount\":10}],\"top_bonus\":[{\"kind\":\"currency\",\"rank\":1,"
                + "\"amount\":50},{\"kind\":\"currency\",\"rank\":3,\"amount\":70}]}");
        EventInstance instance = h.instance(def, EventOutcome.SUCCESS);
        damage(instance, 10, 10);
        h.resolve(instance);
        assertEquals(55L, h.sinks.currency.get(id(1)), "5 of the main line + the rank-1 bonus");
        assertEquals(5L, h.sinks.currency.get(id(2)));
    }

    @Test
    void aLoneParticipantTakesTheWholeLineAndTheRankOneBonus() {
        RewardHarness h = new RewardHarness();
        EventDefinition def = TestInstances.definition("solo_bonus", "reward", "{\"budget\":10000,"
                + "\"lines\":[{\"kind\":\"currency\",\"amount\":10}],\"top_bonus\":[{\"kind\":\"currency\",\"rank\":1,"
                + "\"amount\":50}]}");
        EventInstance instance = h.instance(def, EventOutcome.SUCCESS);
        damage(instance, 10);
        h.resolve(instance);
        assertEquals(60L, h.sinks.currency.get(id(1)), "the whole main line + the rank-1 bonus");
    }

    @Test
    void aThrowingSinkOnlyLosesThatLine() {
        RewardHarness h = new RewardHarness();
        h.sinks.throwOnXp = true;
        h.sinks.online.add(id(1));
        EventInstance instance = h.instance(def("throwing", "reward", 1000, null), EventOutcome.SUCCESS);
        damage(instance, 20);
        h.resolve(instance);
        assertEquals(100L, h.sinks.currency.get(id(1)), "currency still delivered");
        assertEquals(4, stacks(h, 1), "items still delivered");
        assertEquals(List.of("give_xp"), h.failures, "one telemetry report");
    }

    @Test
    void aSecondResolveOfTheSameInstancePaysNobodyTwice() {
        RewardHarness h = new RewardHarness();
        EventInstance instance = h.instance(def("twice", "reward", 1000, null), EventOutcome.SUCCESS);
        damage(instance, 20);
        h.resolve(instance);
        RewardOutcome again = h.resolve(instance);
        assertEquals(1, again.duplicates());
        assertEquals(100L, h.sinks.currency.get(id(1)));
    }
}
