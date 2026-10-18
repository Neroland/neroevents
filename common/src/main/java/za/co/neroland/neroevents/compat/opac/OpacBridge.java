package za.co.neroland.neroevents.compat.opac;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * What NeroEvents needs from Open Parties and Claims. A pure interface: it imports nothing from OPAC; the
 * one implementation that does is loaded by {@link java.util.ServiceLoader} only when OPAC is present, and
 * callers go through the façade, which catches anything a method throws.
 *
 * <p>Every method <b>may throw</b> (a sibling API change surfaces as a {@code RuntimeException} or a
 * {@code LinkageError}).
 */
public interface OpacBridge {

    /** Whether the chunk is claimed by anyone; throws on any OPAC failure (the source fails closed). */
    boolean isChunkClaimed(MinecraftServer server, Identifier dimension, int chunkX, int chunkZ);
}
