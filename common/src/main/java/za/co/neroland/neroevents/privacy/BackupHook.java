package za.co.neroland.neroevents.privacy;

import net.minecraft.server.MinecraftServer;

/**
 * The single place every player-keyed store's post-erasure backup refresh goes through. Production runs
 * the refresh (Core's {@code SavedDataRecovery.backupNow}); the erasure conformance test swaps in a counter
 * so it can prove every persisted store refreshes its backup exactly once per erasure.
 */
public final class BackupHook {

    /** One backup refresh: {@code backup} is the real {@code SavedDataRecovery.backupNow} call. */
    @FunctionalInterface
    public interface Sink {
        void backup(MinecraftServer server, String store, Runnable backup);
    }

    /** Runs the refresh. */
    public static final Sink DIRECT = (server, store, backup) -> backup.run();

    private static volatile Sink current = DIRECT;

    private BackupHook() {
    }

    public static Sink current() {
        return current;
    }

    /** Test seam: {@code null} restores {@link #DIRECT}. */
    public static void set(Sink sink) {
        current = sink == null ? DIRECT : sink;
    }
}
