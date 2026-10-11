package za.co.neroland.neroevents.economy;

import java.util.regex.Pattern;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * Asks NeroEconomy for a market shock (a temporary price multiplier on an item or tag) through Neroland
 * Core's {@link ThresholdEvents}, on channel {@code neroevents:market_shock_request}. Class-free: it needs
 * only Core, and with NeroEconomy absent the request is simply heard by nobody.
 *
 * <p>The crossing carries {@code scope} = the item id or {@code #tag}, {@code value} = the multiplier
 * &times; 1000, {@code threshold} = the duration in economy-minutes and {@code rising} = {@code true} to
 * apply or {@code false} to end the shock early. NeroEconomy clamps the multiplier to its price band and
 * the duration to its {@code shockMaxMinutes} (1,440 by default), caps the active shocks
 * ({@code maxActiveShocks}, 64) and only hears mods listed in its {@code shockSources} config (default
 * {@code neroevents}). Requests are validated here first so a malformed one is never sent.
 *
 * <p>Server thread only. Privacy: a shock names goods, never a player.
 */
public final class MarketShockRequests {

    /** The channel NeroEconomy listens on for NeroEvents' requests. */
    public static final Identifier CHANNEL =
            Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "market_shock_request");

    /** The largest multiplier sent (NeroEconomy clamps further to its own price band). */
    public static final double MAX_MULTIPLIER = 1000.0D;

    /** The longest duration sent, in economy-minutes (a year; NeroEconomy clamps to its shockMaxMinutes). */
    public static final long MAX_MINUTES = 525_600L;

    /** Same shape NeroEconomy accepts: an item id or a tag with a leading {@code #}. */
    private static final Pattern SCOPE = Pattern.compile("#?[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");

    /** What a request did. */
    public enum Result { SENT, INVALID_SCOPE, INVALID_MULTIPLIER, INVALID_DURATION }

    private MarketShockRequests() {
    }

    /**
     * Requests a shock on {@code scope}: {@code multiplier} 1.5 raises prices by 50 %, 0.7 lowers them by 30 %.
     * A new request on a scope with an active shock replaces it.
     *
     * @param scope      an item id ({@code minecraft:iron_ingot}) or tag ({@code #c:ingots/iron})
     * @param multiplier more than 0 and at most {@link #MAX_MULTIPLIER}
     * @param minutes    economy-minutes, 1 to {@link #MAX_MINUTES}
     */
    public static Result apply(String scope, double multiplier, long minutes) {
        Result check = validate(scope, multiplier, minutes);
        if (check != Result.SENT) return check;
        send(scope, toValue(multiplier), minutes, true);
        return Result.SENT;
    }

    /** Ends the shock on {@code scope} early; nothing happens when none is active. */
    public static Result end(String scope) {
        if (!validScope(scope)) return Result.INVALID_SCOPE;
        send(scope, 1000L, 1L, false);
        return Result.SENT;
    }

    /** The checks {@link #apply} makes, without sending anything. */
    public static Result validate(String scope, double multiplier, long minutes) {
        if (!validScope(scope)) return Result.INVALID_SCOPE;
        if (!Double.isFinite(multiplier) || multiplier <= 0.0D || multiplier > MAX_MULTIPLIER
                || toValue(multiplier) < 1L) {
            return Result.INVALID_MULTIPLIER;
        }
        if (minutes < 1L || minutes > MAX_MINUTES) return Result.INVALID_DURATION;
        return Result.SENT;
    }

    /** Whether {@code scope} is an item id or {@code #tag} NeroEconomy would accept. */
    public static boolean validScope(String scope) {
        return scope != null && SCOPE.matcher(scope).matches();
    }

    /** The multiplier as the crossing's value: multiplier &times; 1000, rounded. */
    static long toValue(double multiplier) {
        return Math.round(multiplier * 1000.0D);
    }

    private static void send(String scope, long value, long minutes, boolean apply) {
        try {
            ThresholdEvents.fire(new ThresholdEvents.ThresholdCrossing(CHANNEL, scope, value, minutes, apply));
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsCommon.LOGGER.debug("[NeroEvents] A market-shock request could not be sent ({}).",
                    failure.getClass().getName());
        }
    }
}
