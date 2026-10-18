package za.co.neroland.neroevents.client;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleSnapshot;

/**
 * The client's copy of the last {@link ConsoleSnapshot} and a version counter the console screen polls to know
 * an answer arrived (the snapshot hold). References no client-only class, so the payload handler can be declared
 * on both sides. Wiped on disconnect.
 */
public final class ClientConsoleState {

    private static @Nullable ConsoleSnapshot snapshot;
    private static long version;

    private ClientConsoleState() {
    }

    /** The packet handler (client thread). */
    public static synchronized void onSnapshot(ConsoleSnapshot value) {
        snapshot = value;
        version++;
    }

    public static synchronized @Nullable ConsoleSnapshot snapshot() {
        return snapshot;
    }

    /** Bumped by every snapshot. */
    public static synchronized long version() {
        return version;
    }

    /** Forgets the snapshot (disconnect). */
    public static synchronized void clear() {
        snapshot = null;
        version++;
    }
}
