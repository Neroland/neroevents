package za.co.neroland.neroevents.economy;

import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.neroeconomy.api.EconomyCaller;
import za.co.neroland.neroeconomy.api.EconomyResult;
import za.co.neroland.neroeconomy.api.MarketShocks;
import za.co.neroland.neroeconomy.api.SiblingEconomy;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * The <b>only</b> class in NeroEvents that imports {@code za.co.neroland.neroeconomy}. NeroEconomy is compiled
 * {@code compileOnly} and is optional, so this class is loaded only after {@link EventsEconomy#init()} has seen
 * {@code isModLoaded("neroeconomy")}; nothing else may reference NeroEconomy types.
 *
 * <p>Privacy (POPIA/GDPR): takes only the UUID the caller already holds, passes it straight to NeroEconomy,
 * and never logs it.
 */
final class NeroEconomyBridge {

    /** Claimed once at common init; {@code null} until then (and forever when the claim failed). */
    private static volatile EconomyCaller caller;

    private NeroEconomyBridge() {
    }

    /** Claims NeroEvents's handle with its closed set of reason codes. Common init only. */
    static void claim() {
        caller = SiblingEconomy.claim(NeroEventsCommon.MOD_ID, Set.of(EventsEconomy.REASON_ENTRY, EventsEconomy.REASON_REWARD));
    }

    static EconomyOutcome charge(MinecraftServer server, UUID player, long amount, String reason, String requestKey) {
        EconomyCaller handle = caller;
        if (handle == null) return EconomyOutcome.absentOutcome();
        return outcome(handle.charge(server, player, amount, reason, requestKey));
    }

    static EconomyOutcome pay(MinecraftServer server, UUID player, long amount, String reason, String requestKey) {
        EconomyCaller handle = caller;
        if (handle == null) return EconomyOutcome.absentOutcome();
        return outcome(handle.pay(server, player, amount, reason, requestKey));
    }

    /**
     * Whether NeroEconomy has an active shock on {@code key} (an item id or {@code #tag}); {@code false} when it
     * cannot be asked. Read-only: NeroEvents never changes prices itself.
     */
    static boolean shockActive(String key) {
        if (key == null || key.isBlank()) return false;
        try {
            return MarketShocks.active(key);
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsCommon.LOGGER.debug("[NeroEvents] Could not read NeroEconomy's market shocks ({}).",
                    failure.getClass().getName());
            return false;
        }
    }

    private static EconomyOutcome outcome(EconomyResult result) {
        if (result == null) return EconomyOutcome.invalid();
        return result.ok() ? EconomyOutcome.applied(result.translationKey())
                : EconomyOutcome.refused(result.translationKey());
    }
}
