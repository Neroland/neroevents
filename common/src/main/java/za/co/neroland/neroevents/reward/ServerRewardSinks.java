package za.co.neroland.neroevents.reward;

import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.reputation.ReputationApi;

import za.co.neroland.neroevents.economy.EconomyOutcome;
import za.co.neroland.neroevents.economy.EventsEconomy;
import za.co.neroland.neroevents.event.EventView;
import za.co.neroland.neroevents.event.announce.Announcer;

/**
 * The running-server {@link RewardSinks}: NeroEconomy through {@link EventsEconomy}, vanilla experience for
 * online players, Core's {@link ReputationApi} only when a real provider is bound, chat messages to the one
 * player concerned, and instance notes through the announcer. Server thread only.
 */
public final class ServerRewardSinks implements RewardSinks {

    private final MinecraftServer server;
    private final Announcer announcer;

    public ServerRewardSinks(MinecraftServer server, Announcer announcer) {
        this.server = server;
        this.announcer = announcer;
    }

    private ServerPlayer online(UUID player) {
        return player == null ? null : server.getPlayerList().getPlayer(player);
    }

    @Override
    public boolean economyActive() {
        return EventsEconomy.isActive();
    }

    @Override
    public EconomyOutcome payCurrency(UUID player, long amount, String requestKey) {
        return EventsEconomy.payReward(server, player, amount, requestKey);
    }

    @Override
    public boolean isOnline(UUID player) {
        return online(player) != null;
    }

    @Override
    public boolean giveXp(UUID player, long xp) {
        ServerPlayer target = online(player);
        if (target == null) {
            return false;
        }
        target.giveExperiencePoints((int) Math.min(Integer.MAX_VALUE, xp));
        return true;
    }

    @Override
    public boolean reputationAvailable() {
        return ReputationApi.hasRealProvider();
    }

    @Override
    public boolean adjustReputation(UUID player, Identifier faction, int delta) {
        if (!ReputationApi.hasRealProvider()) {
            return false;
        }
        ReputationApi.adjust(player, faction, delta);
        return true;
    }

    @Override
    public void tell(UUID player, String key, Object... args) {
        ServerPlayer target = online(player);
        if (target == null) {
            return;
        }
        Object[] given = args == null ? new Object[0] : args;
        Object[] rendered = new Object[given.length];
        for (int i = 0; i < rendered.length; i++) {
            rendered[i] = given[i] instanceof BudgetedRewardResolver.Translatable t
                    ? Component.translatable(t.key()) : given[i];
        }
        target.sendSystemMessage(Component.translatable(key, rendered));
    }

    @Override
    public void broadcast(EventView view, String key) {
        announcer.note(view, key);
    }
}
