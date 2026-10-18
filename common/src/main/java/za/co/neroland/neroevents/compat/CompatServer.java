package za.co.neroland.neroevents.compat;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

/**
 * The running server, for the sibling protection sources. A {@link za.co.neroland.neroevents.protect.ProtectionSource}
 * is only handed a {@code WorldView}, but Open Parties and Claims and NeroColonies answer per server, so the engine
 * binds the server here when it starts on one ({@link #set}).
 *
 * <p>No server bound means nothing can be asked, so {@link #require()} throws and the sources fail closed: the
 * evaluation denies and marks the source failing until a server is bound.
 */
public final class CompatServer {

    private static volatile @Nullable MinecraftServer server;

    private CompatServer() {
    }

    /** Binds the running server (or clears it with {@code null}). */
    public static void set(@Nullable MinecraftServer running) {
        server = running;
    }

    public static @Nullable MinecraftServer current() {
        return server;
    }

    /** The bound server; throws when none is bound, which a protection source turns into a refusal. */
    public static MinecraftServer require() {
        MinecraftServer bound = server;
        if (bound == null) {
            throw new IllegalStateException("No server bound for the compat protection sources");
        }
        return bound;
    }
}
