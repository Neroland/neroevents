package za.co.neroland.neroevents.testing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import za.co.neroland.neroevents.event.ContributionTable.Kind;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.TestInstances;
import za.co.neroland.neroevents.history.EventHistoryState;
import za.co.neroland.neroevents.participation.ParticipationService;
import za.co.neroland.neroevents.participation.WeightedContributionTable;
import za.co.neroland.neroevents.privacy.PrivacyOptOuts;
import za.co.neroland.neroevents.reward.BudgetedRewardResolver;
import za.co.neroland.neroevents.reward.RewardConfig;
import za.co.neroland.neroevents.reward.RewardCrateState;
import za.co.neroland.neroevents.reward.RewardCrates;
import za.co.neroland.neroevents.reward.RewardLedgerState;
import za.co.neroland.neroevents.reward.RewardOutcome;

/** The budgeted resolver over real (in-memory) stores and recording fakes. */
public final class RewardHarness {

    public final RewardConfig cfg = RewardConfig.defaults();
    public final FakeRewardSinks sinks = new FakeRewardSinks();
    public final RewardLedgerState ledger = new RewardLedgerState();
    public final RewardCrateState crateState = new RewardCrateState();
    public final RewardCrates crates = new RewardCrates(() -> crateState);
    public final EventHistoryState history = new EventHistoryState();
    public final PrivacyOptOuts optOuts = new PrivacyOptOuts();
    public final RecordingMilestones milestones = new RecordingMilestones();
    public final ParticipationService participation = new ParticipationService(() -> milestones, () -> 100);
    public final FixedClock clock = new FixedClock(Instant.parse("2026-10-17T12:00:00Z").toEpochMilli());
    public final List<String> failures = new ArrayList<>();
    public final BudgetedRewardResolver resolver;

    public RewardHarness() {
        resolver = new BudgetedRewardResolver(() -> cfg, sinks, () -> ledger, crates, () -> history, () -> optOuts,
                () -> milestones, participation, clock);
        resolver.failures((failure, operation) -> failures.add(operation));
    }

    public static UUID id(int n) {
        return new UUID(0L, n);
    }

    /** An instance of {@code def} that ended with {@code outcome}, with the participation listener attached. */
    public EventInstance instance(EventDefinition def, EventOutcome outcome) {
        EventInstance instance = TestInstances.instance(def, outcome);
        if (instance.contributions() instanceof WeightedContributionTable table) {
            table.listener(participation.listenerFor(instance));
        }
        return instance;
    }

    /** Credits {@code damage[i]} damage to {@code id(i + 1)}. */
    public static void damage(EventInstance instance, double... damage) {
        for (int i = 0; i < damage.length; i++) {
            instance.contributions().credit(id(i + 1), Kind.DAMAGE, damage[i]);
        }
    }

    public RewardOutcome resolve(EventInstance instance) {
        return resolver.resolve(instance);
    }
}
