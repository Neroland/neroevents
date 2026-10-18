package za.co.neroland.neroevents.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.economy.EconomyOutcome;
import za.co.neroland.neroevents.event.EventView;
import za.co.neroland.neroevents.reward.RewardSinks;

/** Records every delivery; economy, presence, reputation and refusals are scriptable. */
public final class FakeRewardSinks implements RewardSinks {

    public boolean economy = true;
    public boolean reputation;
    public final Set<UUID> online = new HashSet<>();
    public final Set<String> refuseKeys = new HashSet<>();
    public boolean throwOnXp;
    /** Runs at the start of every currency payment (the write-ahead test checks the ledger here). */
    public java.util.function.Consumer<UUID> beforePay = player -> {
    };
    public final Map<UUID, Long> currency = new HashMap<>();
    public final Map<UUID, Long> xp = new HashMap<>();
    public final Map<UUID, Integer> reputationDeltas = new HashMap<>();
    public final List<String> calls = new ArrayList<>();
    public final List<String> told = new ArrayList<>();
    public final List<String> broadcasts = new ArrayList<>();
    /** Order of events across the ledger and the sinks (the write-ahead test reads it). */
    public final List<String> order;

    public FakeRewardSinks() {
        this(new ArrayList<>());
    }

    public FakeRewardSinks(List<String> order) {
        this.order = order;
    }

    @Override
    public boolean economyActive() {
        return economy;
    }

    @Override
    public EconomyOutcome payCurrency(UUID player, long amount, String requestKey) {
        beforePay.accept(player);
        calls.add("pay:" + requestKey);
        order.add("sink");
        if (refuseKeys.contains(requestKey)) {
            return new EconomyOutcome(EconomyOutcome.Status.REFUSED, "economy_result.neroeconomy.refused");
        }
        currency.merge(player, amount, Long::sum);
        return new EconomyOutcome(EconomyOutcome.Status.APPLIED, "economy_result.neroeconomy.applied");
    }

    @Override
    public boolean isOnline(UUID player) {
        return online.contains(player);
    }

    @Override
    public boolean giveXp(UUID player, long amount) {
        order.add("sink");
        if (throwOnXp) {
            throw new IllegalStateException("xp sink failure");
        }
        if (!online.contains(player)) {
            return false;
        }
        xp.merge(player, amount, Long::sum);
        return true;
    }

    @Override
    public boolean reputationAvailable() {
        return reputation;
    }

    @Override
    public boolean adjustReputation(UUID player, Identifier faction, int delta) {
        if (!reputation) {
            return false;
        }
        reputationDeltas.merge(player, delta, Integer::sum);
        return true;
    }

    @Override
    public void tell(UUID player, String key, Object... args) {
        told.add(key);
    }

    @Override
    public void broadcast(EventView view, String key) {
        broadcasts.add(key);
    }
}
