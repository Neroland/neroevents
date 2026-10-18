package za.co.neroland.neroevents.reward;

import java.util.List;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.economy.EconomyOutcome;
import za.co.neroland.neroevents.event.EventView;

/**
 * Where the resolver delivers: the Minecraft-bound side of rewarding, behind one interface so the whole
 * split, budget and ledger logic runs on a plain JVM (tests use a recording fake). Every method is called
 * on the server thread for one participant the resolver already holds.
 */
public interface RewardSinks {

    /** Whether NeroEconomy is installed and hooked. */
    boolean economyActive();

    /** Pays credits through NeroEconomy (offline players too: it takes a UUID). */
    EconomyOutcome payCurrency(UUID player, long amount, String requestKey);

    boolean isOnline(UUID player);

    /** Gives experience to an online player; {@code false} if they are offline (it then goes to the crate). */
    boolean giveXp(UUID player, long xp);

    /** Whether a real reputation provider (NeroFactions) is bound. */
    boolean reputationAvailable();

    /** Applies a reputation change; {@code false} when no real provider is bound. */
    boolean adjustReputation(UUID player, Identifier faction, int delta);

    /** A translatable message to one online player (nothing for an offline one). */
    void tell(UUID player, String key, Object... args);

    /** A translatable note about an instance to everyone (counts only, never a player). */
    void broadcast(EventView view, String key);

    /** Reward crate stacks are stored through {@link RewardCrates}; the resolver passes these through. */
    default List<CrateEntry.Stack> stacks(Identifier item, int count) {
        return List.of(new CrateEntry.Stack(item, count));
    }
}
