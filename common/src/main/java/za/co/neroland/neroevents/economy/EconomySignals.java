package za.co.neroland.neroevents.economy;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * NeroEconomy's world signals, heard class-free through Neroland Core's {@link ThresholdEvents} and handed
 * to NeroEvents' own hooks. This is the seam the event system uses to react to the economy (a sector crash
 * could start a world event, for example); until it has content no hook is registered and a signal does
 * nothing.
 *
 * <table>
 *   <caption>Channels</caption>
 *   <tr><th>Kind</th><th>Scope</th><th>Value</th><th>Threshold</th><th>rising</th></tr>
 *   <tr><td>{@link Kind#SECTOR_INDEX}</td><td>sector word</td><td>index in hundredths of a credit</td>
 *       <td>the band level crossed</td><td>direction of the crossing</td></tr>
 *   <tr><td>{@link Kind#MARKET_SHOCK}</td><td>item id or {@code #tag}</td><td>multiplier &times; 1000</td>
 *       <td>1000</td><td>{@code true} when a shock starts, {@code false} when it ends</td></tr>
 *   <tr><td>{@link Kind#RESERVE}</td><td>{@code world}</td><td>the Reserve's balance</td>
 *       <td>the low-alert level</td><td>{@code false} below it, {@code true} recovered</td></tr>
 *   <tr><td>{@link Kind#HOUSE_STOCK}</td><td>commodity key</td><td>units a Merchant House holds</td>
 *       <td>0</td><td>{@code false} sold out, {@code true} restocked</td></tr>
 * </table>
 *
 * <p>Hooks run on the server thread. A hook that throws is skipped (logged by exception class only) and
 * the others still run. Privacy: every signal names a place, sector or good, never a player, and nothing
 * here logs a signal's content.
 */
public final class EconomySignals {

    /** The namespace NeroEconomy publishes on. */
    public static final String NAMESPACE = "neroeconomy";

    /** One of NeroEconomy's world channels. */
    public enum Kind {
        SECTOR_INDEX, MARKET_SHOCK, RESERVE, HOUSE_STOCK;

        /** The channel path, e.g. {@code sector_index}. */
        public String path() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Kind byPath(String path) {
            for (Kind kind : values()) {
                if (kind.path().equals(path)) return kind;
            }
            return null;
        }
    }

    /** One crossing on a NeroEconomy world channel. */
    public record EconomySignal(Kind kind, String scope, long value, long threshold, boolean rising) {
    }

    private static final List<Consumer<EconomySignal>> HOOKS = new CopyOnWriteArrayList<>();
    private static boolean registered;

    private EconomySignals() {
    }

    /** Starts listening on Core's threshold bus. Call once from common init; Core is all it needs. */
    public static synchronized void init() {
        if (registered) return;
        registered = true;
        ThresholdEvents.onCrossing(EconomySignals::onCrossing);
    }

    /** Adds a hook that receives every NeroEconomy world signal (server thread). */
    public static void addHook(Consumer<EconomySignal> hook) {
        if (hook != null) HOOKS.add(hook);
    }

    /** Removes a hook added with {@link #addHook}. */
    public static void removeHook(Consumer<EconomySignal> hook) {
        HOOKS.remove(hook);
    }

    /** Turns a crossing into a signal, or {@code null} when it is not one of NeroEconomy's world channels. */
    static EconomySignal decode(ThresholdEvents.ThresholdCrossing crossing) {
        if (crossing == null || crossing.channel() == null) return null;
        if (!NAMESPACE.equals(crossing.channel().getNamespace())) return null;
        Kind kind = Kind.byPath(crossing.channel().getPath());
        if (kind == null) return null;
        return new EconomySignal(kind, crossing.scope(), crossing.value(), crossing.threshold(), crossing.rising());
    }

    private static void onCrossing(ThresholdEvents.ThresholdCrossing crossing) {
        if (HOOKS.isEmpty()) return;
        EconomySignal signal = decode(crossing);
        if (signal == null) return;
        for (Consumer<EconomySignal> hook : HOOKS) {
            try {
                hook.accept(signal);
            } catch (RuntimeException failure) {
                NeroEventsCommon.LOGGER.debug("[NeroEvents] An economy-signal hook threw ({}).",
                        failure.getClass().getName());
            }
        }
    }
}
