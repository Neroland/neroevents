package za.co.neroland.neroevents.event.world;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * The pure-Java seam between the event engine and the running world. Everything the scheduler, the
 * anchor picker and the protection sources decide is computed against this interface, so every engine
 * test runs on a plain JVM with a fake.
 *
 * <p><b>Privacy by construction:</b> no method here returns a player's position, name or UUID. Players
 * appear only as counts — per server, per dimension, per density cell, per open gate, per stand-off circle. The anchor
 * picker therefore cannot aim at a specific player even by accident.
 *
 * <p>Dimensions are {@link Identifier}s; conversion to a level happens once, in the server adapter.
 * Chunk accessors never load or generate a chunk.
 */
public interface WorldView {

    /** Overworld game time (persists across restarts, does not advance while the server is down). */
    long gameTime();

    boolean isDedicatedServer();

    int onlinePlayers();

    Map<Identifier, Integer> onlinePlayersPerDimension();

    List<Identifier> loadedDimensions();

    /** Whether the dimension's dimension type is in the given dimension-type tag. */
    boolean dimensionInTag(Identifier dimension, Identifier dimensionTypeTag);

    /** The dimension's time of day, 0..23999. */
    long dayTime(Identifier dimension);

    /** How many online players have the Core gate open. */
    int playersWithGateOpen(Identifier gate);

    boolean isModLoaded(String modId);

    /** Online-player counts per density cell ({@code cellBlocks} square), never positions. Key = packed cell. */
    Map<Long, Integer> densityCells(Identifier dimension, int cellBlocks);

    /**
     * How many online players in that dimension stand within Chebyshev distance {@code blocks} of the
     * column ({@code x}, {@code z}). A count, never a position.
     */
    int playersWithinBlocks(Identifier dimension, int x, int z, int blocks);

    /** Whether the chunk is loaded right now; never loads it. */
    boolean isChunkLoaded(Identifier dimension, int chunkX, int chunkZ);

    /** The surface height at a column; empty if the chunk is not loaded. */
    OptionalInt surfaceY(Identifier dimension, int x, int z);

    /** The world spawn, when it lies in this dimension. */
    Optional<BlockPos> spawnPosition(Identifier dimension);

    /** The server's own spawn-protection radius (0 when it has none). */
    int spawnProtectionRadius();

    /** Registry namespaces of every block entity in a loaded chunk; empty if unloaded. */
    Set<String> blockEntityNamespaces(Identifier dimension, int chunkX, int chunkZ);

    int minBuildHeight(Identifier dimension);

    int maxBuildHeight(Identifier dimension);
}
