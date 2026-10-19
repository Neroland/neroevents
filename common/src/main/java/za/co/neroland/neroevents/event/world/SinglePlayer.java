package za.co.neroland.neroevents.event.world;

import net.minecraft.server.MinecraftServer;

/**
 * The one single-player rule: an integrated (non-dedicated) server with at most one player online. A
 * world opened to LAN with nobody connected is still one person playing. It is the same rule the consent
 * mode applies, so pacing, consent and wording can never disagree. Never cached: evaluate where used.
 */
public final class SinglePlayer {

    private SinglePlayer() {
    }

    /** An integrated (non-dedicated) server with at most one player online. */
    public static boolean is(MinecraftServer server) {
        return !server.isDedicatedServer() && server.getPlayerCount() <= 1;
    }

    /** The same rule on the pure seam, so every engine test can flip it. */
    public static boolean is(WorldView world) {
        return !world.isDedicatedServer() && world.onlinePlayers() <= 1;
    }
}
