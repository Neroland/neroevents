package za.co.neroland.neroevents.client;

import java.util.List;

import za.co.neroland.neroevents.console.ConsolePayloads.HudEntry;
import za.co.neroland.neroevents.console.ConsolePayloads.HudState;

/**
 * The client's mirror of the last {@link HudState}: what the HUD draws, plus the local countdown between two
 * pushes and the "dismissed until the next phase change" flag. References no client-only class, so the payload
 * handler can be declared on both sides. Wiped on disconnect.
 */
public final class ClientHudState {

    /** A HUD older than this (no push arrived) is treated as gone. */
    public static final int STALE_TICKS = 200;

    private static List<HudEntry> entries = List.of();
    private static long clientTicks;
    private static long receivedAt;
    private static String phaseSignature = "";
    private static boolean dismissed;

    private ClientHudState() {
    }

    /** The packet handler (client thread). */
    public static synchronized void onState(HudState state) {
        entries = state.entries();
        receivedAt = clientTicks;
        String signature = signature(entries);
        if (!signature.equals(phaseSignature)) {
            phaseSignature = signature;
            dismissed = false; // the dismiss key hides the HUD only until the next phase change
        }
    }

    /** Which events are shown in which phase: a change of it is "the next phase change". */
    static String signature(List<HudEntry> list) {
        StringBuilder out = new StringBuilder();
        for (HudEntry entry : list) {
            out.append(entry.key()).append(':').append(entry.phase()).append(';');
        }
        return out.toString();
    }

    /** One client tick (counts the local countdown; drops a stale HUD). */
    public static synchronized void tick() {
        clientTicks++;
        if (!entries.isEmpty() && clientTicks - receivedAt > STALE_TICKS) {
            entries = List.of();
        }
    }

    /** The entries to draw, most urgent first (empty while dismissed). */
    public static synchronized List<HudEntry> visible() {
        return dismissed ? List.of() : entries;
    }

    /** Every entry regardless of the dismiss flag. */
    public static synchronized List<HudEntry> entries() {
        return entries;
    }

    /** Ticks since the last push (the HUD subtracts this from {@code ticksLeft} and adds it to {@code phaseAge}). */
    public static synchronized int sinceUpdate() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, clientTicks - receivedAt));
    }

    /** The dismiss key: hide until the next phase change. */
    public static synchronized void dismiss() {
        if (!entries.isEmpty()) {
            dismissed = true;
        }
    }

    public static synchronized boolean dismissed() {
        return dismissed;
    }

    /** Forgets everything (disconnect). */
    public static synchronized void clear() {
        entries = List.of();
        phaseSignature = "";
        dismissed = false;
        receivedAt = clientTicks;
    }
}
