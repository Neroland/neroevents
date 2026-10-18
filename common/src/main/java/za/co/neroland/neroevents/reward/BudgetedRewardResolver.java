package za.co.neroland.neroevents.reward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.economy.EconomyOutcome;
import za.co.neroland.neroevents.event.EventCategory;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.EventView;
import za.co.neroland.neroevents.event.RewardSpec;
import za.co.neroland.neroevents.event.reward.RewardResolver;
import za.co.neroland.neroevents.history.EventHistoryState;
import za.co.neroland.neroevents.participation.ParticipationService;
import za.co.neroland.neroevents.privacy.PrivacyOptOuts;
import za.co.neroland.neroevents.progression.Milestones;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The reward resolver: at RESOLVING it fires the participation part of the quest contract, then pays the
 * definition's lines out of one pool bounded three ways — the definition's budget, what the lines are
 * worth, and what is left of today's (UTC) world budget — split by contribution in integer arithmetic.
 *
 * <ul>
 *   <li><b>Currency</b> through NeroEconomy as the declared {@code event_reward} source (skipped, with one
 *       INFO line per instance, on a server without NeroEconomy).</li>
 *   <li><b>Items</b> into the reward crate, never into the world; <b>XP</b> directly when online, otherwise
 *       into the crate.</li>
 *   <li><b>Reputation</b> only with a real reputation provider, capped per event.</li>
 *   <li><b>Paid once:</b> the ledger row (or, for an opted-out player or a server without a ledger, a
 *       session marker) is written <em>before</em> any sink runs, so a crash in between loses a reward rather
 *       than paying twice.</li>
 * </ul>
 *
 * <p>Never throws out: a failing sink is reported (telemetry {@code source=rewards}) and the grant carries on
 * with its other lines. Nothing here logs a UUID, an amount for a player or a share above DEBUG. Server
 * thread only.
 */
public final class BudgetedRewardResolver implements RewardResolver {

    /** Where handled failures go (telemetry in production; a counter in tests). */
    @FunctionalInterface
    public interface FailureSink {
        void capture(Throwable failure, String operation);
    }

    /** A translation key passed as a message argument (the sink renders it translated). */
    public record Translatable(String key) {
    }

    private static final long SCALE = RewardSplit.SCALE;

    private final Supplier<RewardConfig> config;
    private final RewardSinks sinks;
    private final Supplier<RewardLedgerState> ledger;
    private final RewardCrates crates;
    private final Supplier<EventHistoryState> history;
    private final Supplier<PrivacyOptOuts> optOuts;
    private final Supplier<Milestones> milestones;
    private final ParticipationService participation;
    private final LongSupplier wallClock;
    private FailureSink failures = (failure, operation) ->
            NeroEventsTelemetry.captureHandledException(failure, "rewards", operation);

    public BudgetedRewardResolver(Supplier<RewardConfig> config, RewardSinks sinks, Supplier<RewardLedgerState> ledger,
            RewardCrates crates, Supplier<EventHistoryState> history, Supplier<PrivacyOptOuts> optOuts,
            Supplier<Milestones> milestones, ParticipationService participation, LongSupplier wallClock) {
        this.config = config;
        this.sinks = sinks;
        this.ledger = ledger;
        this.crates = crates;
        this.history = history;
        this.optOuts = optOuts;
        this.milestones = milestones;
        this.participation = participation;
        this.wallClock = wallClock;
    }

    /** Test seam: where handled sink failures are reported. */
    public void failures(FailureSink sink) {
        this.failures = sink;
    }

    public RewardCrates crates() {
        return crates;
    }

    @Override
    public long remainingDailyBudget(long nowEpochMillis) {
        return history.get().budget(nowEpochMillis).remaining(config.get().rewardBudgetPerDay);
    }

    @Override
    public void forget(UUID player) {
        crates.forgetSession(player);
    }

    @Override
    public boolean holds(UUID player) {
        return crates.holdsSession(player);
    }

    // --- one participant's planned grant ------------------------------------------------------------------

    private static final class Grant {
        final RewardShare share;
        final List<long[]> currency = new ArrayList<>(); // {amount, lineIndex}
        long xp;
        final Map<Identifier, Integer> items = new LinkedHashMap<>();
        final Map<Identifier, Long> itemValue = new LinkedHashMap<>();
        final List<RewardLine.Reputation> reputation = new ArrayList<>();

        Grant(RewardShare share) {
            this.share = share;
        }

        boolean empty() {
            return currency.isEmpty() && xp <= 0L && items.isEmpty() && reputation.isEmpty();
        }
    }

    // --- resolve ----------------------------------------------------------------------------------------

    @Override
    public RewardOutcome resolve(EventInstance instance) {
        RewardConfig cfg = config.get();
        EventDefinition def = instance.definition();
        RewardSpec spec = def.rewards();
        EventOutcome outcome = instance.outcome();
        EventCategory category = def.category();
        List<RewardShare> ranked = instance.contributions().ranked();
        int credited = ranked.size();
        long now = wallClock.getAsLong();
        boolean payable = outcome == EventOutcome.SUCCESS || outcome == EventOutcome.ENDED
                || (outcome == EventOutcome.EXPIRED && spec.payOnExpiry());

        // The quest contract that depends only on participation (fires even when nothing is payable).
        if ((category == EventCategory.RAID || category == EventCategory.INVASION) && outcome == EventOutcome.SUCCESS) {
            for (RewardShare share : ranked) {
                guard("milestones", () -> milestones.get().recordRaidRepelled(share.player()));
            }
        }
        long charged = 0L;
        if (category == EventCategory.BOSS && outcome == EventOutcome.SUCCESS) {
            if (credited == 0) {
                return RewardOutcome.escapedOutcome();
            }
            long minPpm = Math.round(cfg.bossContributorMinShare * SCALE);
            for (RewardShare share : ranked) {
                if (share.sharePpm() >= minPpm) {
                    guard("milestones", () -> milestones.get().recordBossContributor(share.player()));
                }
            }
            guard("milestones", () -> milestones.get().fireBossDefeated(def.id()));
            // The boss mod pays its own drops per credited participant: charged to today's budget first.
            long external = cfg.bossCreaturesRewardValuePerParticipant * credited;
            if (external > 0L) {
                long ext = external;
                guard("history", () -> history.get().charge(now, ext));
                charged = external;
            }
        }

        List<RewardLine> main = new ArrayList<>();
        for (RewardSpec.RewardLine line : spec.lines()) {
            main.add(RewardLine.of(line));
        }
        List<RewardLine> bonus = new ArrayList<>();
        for (RewardSpec.RewardLine line : spec.topBonus()) {
            bonus.add(RewardLine.of(line));
        }
        if (!payable || credited == 0 || (main.isEmpty() && bonus.isEmpty())) {
            return RewardOutcome.charged(credited, charged, false);
        }

        ValueTable values = cfg.values();
        long remaining = history.get().budget(now).remaining(cfg.rewardBudgetPerDay);
        List<RewardLine> all = new ArrayList<>(main);
        all.addAll(bonus);
        long total = values.total(all);
        long pool = Math.min(spec.budget(), Math.min(total, remaining));
        if (remaining == 0L && total > 0L) {
            if (cfg.rewardBudgetPerDay > 0L) { // a cap of 0 pays nothing and says nothing
                guard("history", () -> sinks.broadcast(instance.view(instance.phaseSince()),
                        "announce.neroevents.budget_exhausted"));
            }
            return RewardOutcome.charged(credited, charged, true);
        }
        long factorPpm = total == 0L ? 0L : RewardSplit.mulDiv(pool, SCALE, total);

        List<RewardShare> shares = RewardSplit.shares(ranked, spec.minShareFraction(),
                spec.split() == RewardSpec.Split.EQUAL);
        Map<UUID, Grant> grants = new LinkedHashMap<>();
        for (RewardShare share : shares) {
            grants.put(share.player(), new Grant(share));
        }

        int currencyLines = 0;
        for (int i = 0; i < main.size(); i++) {
            RewardLine line = main.get(i);
            if (line instanceof RewardLine.Reputation rep) {
                for (RewardShare share : shares) {
                    if (share.floorEligible()) {
                        grants.get(share.player()).reputation.add(rep);
                    }
                }
                continue;
            }
            long linePool = scaled(line, factorPpm);
            long[] amounts = RewardSplit.amounts(linePool, shares);
            if (line instanceof RewardLine.Currency) {
                currencyLines++;
            }
            for (int j = 0; j < shares.size(); j++) {
                add(grants.get(shares.get(j).player()), line, amounts[j], i, values);
            }
        }
        for (int i = 0; i < bonus.size(); i++) {
            RewardLine line = bonus.get(i);
            int rank = line.rank();
            if (rank < 1 || rank > shares.size()) {
                continue;
            }
            Grant grant = grants.get(shares.get(rank - 1).player());
            if (line instanceof RewardLine.Reputation rep) {
                grant.reputation.add(rep);
                continue;
            }
            if (line instanceof RewardLine.Currency) {
                currencyLines++;
            }
            add(grant, line, scaled(line, factorPpm), main.size() + i, values);
        }

        boolean economy = sinks.economyActive();
        if (!economy && currencyLines > 0) {
            NeroEventsCommon.LOGGER.info("[NeroEvents] {} {}: {} currency line(s) skipped — NeroEconomy not installed",
                    def.id(), instance.shortId(), currencyLines);
        }

        Delivery totals = new Delivery();
        Map<UUID, RewardSummary> summaries = new LinkedHashMap<>();
        boolean reputationNoted = false;
        for (Grant grant : grants.values()) {
            if (grant.empty()) {
                continue;
            }
            if (!grant.reputation.isEmpty() && !sinks.reputationAvailable() && !reputationNoted) {
                reputationNoted = true;
                NeroEventsCommon.LOGGER.debug("[NeroEvents] {} {}: reputation lines skipped: no real provider",
                        def.id(), instance.shortId());
            }
            RewardSummary summary = deliver(instance, def, grant, cfg, values, shares.size(), economy, now, totals);
            if (summary != null) {
                summaries.put(grant.share.player(), summary);
            }
        }
        long paid = totals.value + values.xpValue(totals.xp);
        guard("history", () -> history.get().charge(now, paid));
        RewardOutcome result = new RewardOutcome(pool, factorPpm, summaries, charged + paid, totals.currency,
                totals.xp, totals.stacks, !economy && currencyLines > 0, totals.refused, totals.duplicates, false,
                false, credited);
        if (!summaries.isEmpty()) {
            EventView view = instance.view(instance.phaseSince());
            guard("listener", () -> RewardEvents.publish(new RewardEvents.RewardedBatch(view, summaries)));
        }
        return result;
    }

    /** {@code amount × factor} for currency/XP, {@code count × factor} for items (floor). */
    private static long scaled(RewardLine line, long factorPpm) {
        if (line instanceof RewardLine.Currency c) {
            return RewardSplit.mulDiv(c.amount(), factorPpm, SCALE);
        }
        if (line instanceof RewardLine.Xp x) {
            return RewardSplit.mulDiv(x.amount(), factorPpm, SCALE);
        }
        if (line instanceof RewardLine.Item i) {
            return RewardSplit.mulDiv(i.count(), factorPpm, SCALE);
        }
        return 0L; // reputation is not scaled
    }

    private static void add(Grant grant, RewardLine line, long amount, int lineIndex, ValueTable values) {
        if (amount <= 0L) {
            return;
        }
        if (line instanceof RewardLine.Currency) {
            grant.currency.add(new long[] {amount, lineIndex});
        } else if (line instanceof RewardLine.Xp) {
            grant.xp += amount;
        } else if (line instanceof RewardLine.Item i) {
            grant.items.merge(i.item(), (int) Math.min(Integer.MAX_VALUE, amount), Integer::sum);
            grant.itemValue.putIfAbsent(i.item(), values.itemValue(i));
        } else if (line instanceof RewardLine.Reputation r) {
            grant.reputation.add(r);
        }
    }

    /** Running totals for the instance. */
    private static final class Delivery {
        long value;
        long currency;
        long xp;
        int stacks;
        int refused;
        int duplicates;
    }

    private RewardSummary deliver(EventInstance instance, EventDefinition def, Grant grant, RewardConfig cfg,
            ValueTable values, int participantCount, boolean economy, long now, Delivery totals) {
        UUID player = grant.share.player();
        UUID instanceId = instance.id();
        PrivacyOptOuts outs = optOuts.get();
        boolean optedOut = outs != null && outs.isOptedOut(player);
        boolean ledgerMode = cfg.rewardLedgerEnabled && !optedOut;
        RewardLedgerState rows = ledger.get();
        if ((rows != null && rows.hasPaid(player, instanceId)) || participation.paidThisSession(instanceId, player)) {
            totals.duplicates++;
            return null;
        }

        // Write-ahead: the row (or the session marker) first, then the sinks.
        RewardSummary planned = new RewardSummary(0L, 0L, 0, 0, 0, false, grant.share.rank(), participantCount,
                grant.share.sharePpm());
        RewardLedgerEntry row = null;
        if (cfg.rewardLedgerEnabled && rows != null) {
            try {
                row = rows.record(new RewardLedgerEntry(ledgerMode ? java.util.Optional.of(player)
                        : java.util.Optional.empty(), instanceId, def.id(),
                        ledgerMode ? planned : planned.anonymised(), now), cfg.rewardLedgerMaxEntries);
            } catch (RuntimeException | LinkageError failure) {
                failures.capture(failure, "ledger");
            }
        }
        if (!ledgerMode) {
            participation.markPaid(instanceId, player);
        }

        long currencyPaid = 0L;
        boolean currencySkipped = false;
        for (long[] line : grant.currency) {
            if (!economy) {
                currencySkipped = true;
                continue;
            }
            String key = "ev:" + instance.shortId() + ":" + line[1] + ":" + grant.share.rank();
            try {
                EconomyOutcome result = sinks.payCurrency(player, line[0], key);
                if (result.ok()) {
                    currencyPaid += line[0];
                } else if (result.absent()) {
                    currencySkipped = true;
                } else {
                    totals.refused++;
                    sinks.tell(player, "reward.neroevents.currency_refused", new Translatable(result.translationKey()));
                }
            } catch (RuntimeException | LinkageError failure) {
                failures.capture(failure, "pay_currency");
            }
        }
        if (currencySkipped) {
            sinks.tell(player, "reward.neroevents.currency_skipped");
        }

        long xpGranted = 0L;
        long crateXp = 0L;
        if (grant.xp > 0L) {
            try {
                if (sinks.giveXp(player, grant.xp)) {
                    xpGranted += grant.xp;
                } else {
                    crateXp = grant.xp;
                }
            } catch (RuntimeException | LinkageError failure) {
                failures.capture(failure, "give_xp");
            }
        }

        int stacks = 0;
        int itemCount = 0;
        long itemsValue = 0L;
        if (!grant.items.isEmpty() || crateXp > 0L) {
            List<CrateEntry.Stack> incoming = new ArrayList<>();
            grant.items.forEach((item, count) -> incoming.add(new CrateEntry.Stack(item, count)));
            try {
                RewardCrates.Admission admission = crates.add(player, instanceId, def.id(), incoming, crateXp, now,
                        optedOut, cfg.rewardCrateMaxStacksPerPlayer);
                xpGranted += crateXp;
                for (CrateEntry.Stack stack : admission.stored()) {
                    stacks++;
                    itemCount += stack.count();
                    itemsValue += stack.count() * grant.itemValue.getOrDefault(stack.item(), values.itemDefaultValue());
                }
                if (admission.forfeitedStacks() > 0) {
                    NeroEventsCommon.LOGGER.debug("[NeroEvents] {}: {} reward stack(s) forfeited at the crate cap",
                            instance.shortId(), admission.forfeitedStacks());
                    sinks.tell(player, "reward.neroevents.crate_full");
                }
                if (optedOut && crates.firstSessionNotice(player)) {
                    sinks.tell(player, "reward.neroevents.crate_session_only");
                }
            } catch (RuntimeException | LinkageError failure) {
                failures.capture(failure, "crate_add");
            }
        }

        int reputationLines = 0;
        for (RewardLine.Reputation rep : grant.reputation) {
            if (!sinks.reputationAvailable()) {
                break;
            }
            int cap = Math.max(1, cfg.rewardReputationMaxDeltaPerEvent);
            int delta = Integer.signum(rep.delta()) * Math.min(Math.abs(rep.delta()), cap);
            if (delta == 0) {
                continue;
            }
            try {
                if (sinks.adjustReputation(player, rep.faction(), delta)) {
                    reputationLines++;
                }
            } catch (RuntimeException | LinkageError failure) {
                failures.capture(failure, "reputation");
            }
        }

        RewardSummary actual = new RewardSummary(currencyPaid, xpGranted, stacks, itemCount, reputationLines,
                currencySkipped, grant.share.rank(), participantCount, grant.share.sharePpm());
        if (row != null && rows != null) {
            try {
                rows.amend(row, ledgerMode ? actual : actual.anonymised());
            } catch (RuntimeException | LinkageError failure) {
                failures.capture(failure, "ledger");
            }
        }
        totals.value += currencyPaid + itemsValue; // XP is valued once on the instance total (no per-player rounding)
        totals.currency += currencyPaid;
        totals.xp += xpGranted;
        totals.stacks += stacks;
        if (sinks.isOnline(player)) {
            sinks.tell(player, "reward.neroevents.summary", currencyPaid, xpGranted, stacks,
                    new Translatable(def.display().title()), grant.share.rank(), participantCount);
        }
        return actual;
    }

    private void guard(String operation, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError failure) {
            failures.capture(failure, operation);
        }
    }
}
