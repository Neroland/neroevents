package za.co.neroland.neroevents.creatures;

import java.util.OptionalInt;
import java.util.random.RandomGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.world.WorldView;

/**
 * Spawn positions on the ring around a raid anchor. Reads only the pure {@link WorldView} (loaded-chunk
 * check, then the surface height), so it never loads a chunk and is testable on a plain JVM. The caller
 * protection-checks every position before it reaches {@code spawnWithin}.
 */
public final class WaveSpawner {

    /** Attempts per position before giving up on this mob. */
    public static final int TRIES = 3;

    private WaveSpawner() {
    }

    /**
     * A surface position {@code [rmin, rmax]} blocks from the anchor in a random direction, in a loaded chunk
     * and above the dimension's floor; {@code null} after {@link #TRIES} misses.
     */
    public static @Nullable BlockPos ringPosition(WorldView world, Identifier dimension, BlockPos anchor, int rmin,
            int rmax, RandomGenerator random) {
        int lo = Math.max(0, Math.min(rmin, rmax));
        int hi = Math.max(rmin, rmax);
        int floor = world.minBuildHeight(dimension);
        int ceiling = world.maxBuildHeight(dimension);
        for (int attempt = 0; attempt < TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int radius = lo + random.nextInt(hi - lo + 1);
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * radius);
            if (!world.isChunkLoaded(dimension, x >> 4, z >> 4)) {
                continue;
            }
            OptionalInt surface = world.surfaceY(dimension, x, z);
            if (surface.isEmpty() || surface.getAsInt() <= floor || surface.getAsInt() + 1 >= ceiling) {
                continue; // void or sky-high column: nothing to stand on
            }
            return new BlockPos(x, surface.getAsInt() + 1, z);
        }
        return null;
    }
}
