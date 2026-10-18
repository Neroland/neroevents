package za.co.neroland.neroevents.economy;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.platform.Services;

/**
 * Paid events and event rewards through NeroEconomy, which is optional. The future event system calls
 * {@link #chargeEntry} before admitting a player and {@link #payReward} when a reward is earned.
 *
 * <ul>
 *   <li>Admit only when {@link EconomyOutcome#ok()}; otherwise show {@link EconomyOutcome#message()}.</li>
 *   <li>Rewards are budgeted: NeroEconomy refuses one when the Reserve, NeroEvents' daily budget, the shared
 *       budget or the player's daily cap cannot cover it. Plan events so a busy day running out of budget
 *       is acceptable, and say so to the player in plain words.</li>
 *   <li>With NeroEconomy absent the outcome is {@link EconomyOutcome.Status#ABSENT}: run the event free and
 *       without credit rewards.</li>
 *   <li>A request key names the event occurrence, for example {@code "event:meteor_shower:412:entry"}, never
 *       a player; the same key is applied at most once a day, so a retry cannot charge or pay twice.</li>
 * </ul>
 *
 * <p>Server thread only. Privacy (POPIA/GDPR): takes the player only to pass their UUID to NeroEconomy;
 * nothing here logs who paid or was paid.
 */
public final class EventsEconomy {

    /** Reason code for an event entry fee (player to the Reserve). */
    public static final String REASON_ENTRY = "event_entry";
    /** Reason code for an event reward (Reserve to player, budgeted). */
    public static final String REASON_REWARD = "event_reward";

    /** Whether NeroEconomy is installed and claimed. */
    private static volatile boolean active;

    private EventsEconomy() {
    }

    /**
     * Hooks NeroEconomy when it is installed. Call once from common init (claims close when the first server
     * starts). With NeroEconomy absent this does nothing and every call below returns
     * {@link EconomyOutcome.Status#ABSENT}. There is no runtime version check: the manifests' optional
     * NeroEconomy floor (the compiled version) guarantees the API, and a link failure is caught below.
     */
    public static void init() {
        if (active || !Services.PLATFORM.isModLoaded("neroeconomy")) return;
        try {
            NeroEconomyBridge.claim();
            active = true;
            NeroEventsCommon.LOGGER.info("[NeroEvents] NeroEconomy found; paid events and credit rewards are available.");
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsCommon.LOGGER.warn("[NeroEvents] Could not hook NeroEconomy ({}); paid events and credit rewards stay off.",
                    failure.getClass().getSimpleName());
        }
    }

    /** Whether NeroEconomy is hooked, so paid features can be offered. */
    public static boolean isActive() {
        return active;
    }

    /**
     * Charges an event entry fee into NeroEconomy's Reserve.
     *
     * @param player     the player entering
     * @param fee        the fee in whole credits, more than 0
     * @param requestKey the event occurrence's key (see {@link RequestKeys}), or {@code null} for no replay
     *                   protection
     */
    public static EconomyOutcome chargeEntry(ServerPlayer player, long fee, String requestKey) {
        if (!active) return EconomyOutcome.absentOutcome();
        if (player == null || fee <= 0L || !RequestKeys.isValidOrNull(requestKey)) return EconomyOutcome.invalid();
        return NeroEconomyBridge.charge(player.level().getServer(), player.getUUID(), fee, REASON_ENTRY, requestKey);
    }

    /**
     * Pays an event reward from NeroEconomy's Reserve, within NeroEvents' daily budget.
     *
     * @param player     the player rewarded
     * @param amount     the reward in whole credits, more than 0
     * @param requestKey the reward's key (see {@link RequestKeys}), or {@code null} for no replay protection
     */
    public static EconomyOutcome payReward(ServerPlayer player, long amount, String requestKey) {
        if (player == null) return active ? EconomyOutcome.invalid() : EconomyOutcome.absentOutcome();
        return payReward(player.level().getServer(), player.getUUID(), amount, requestKey);
    }

    /**
     * Pays an event reward to a participant by UUID, online or offline (the reward resolver's path).
     *
     * @param server     the running server
     * @param player     the participant's UUID (passed straight to NeroEconomy, never logged)
     * @param amount     the reward in whole credits, more than 0
     * @param requestKey the grant's key (see {@link RequestKeys}), so a repeated delivery cannot pay twice
     */
    public static EconomyOutcome payReward(MinecraftServer server, UUID player, long amount, String requestKey) {
        if (!active) return EconomyOutcome.absentOutcome();
        if (server == null || player == null || amount <= 0L || !RequestKeys.isValidOrNull(requestKey)) {
            return EconomyOutcome.invalid();
        }
        return NeroEconomyBridge.pay(server, player, amount, REASON_REWARD, requestKey);
    }
}
