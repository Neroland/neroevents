package za.co.neroland.neroevents.event.world;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Online-player counts per density cell, turned into anchor weights: a cell weighs twice its own count
 * plus its eight neighbours' counts, so events land where people are without ever reading a position.
 * In a dimension with nobody in it (allowed only for {@code allow_empty_dimension}), the world-spawn cell
 * and its neighbours weigh 1 each.
 */
public final class DensityCells {

    private final int cellBlocks;
    private final TreeMap<Long, Integer> weights;

    private DensityCells(int cellBlocks, TreeMap<Long, Integer> weights) {
        this.cellBlocks = cellBlocks;
        this.weights = weights;
    }

    public static long pack(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    public static int unpackX(long key) {
        return (int) (key >> 32);
    }

    public static int unpackZ(long key) {
        return (int) key;
    }

    /** The cell holding a block column. */
    public static long cellOf(int x, int z, int cellBlocks) {
        return pack(Math.floorDiv(x, cellBlocks), Math.floorDiv(z, cellBlocks));
    }

    public static DensityCells of(WorldView world, Identifier dimension, int cellBlocks) {
        Map<Long, Integer> counts = world.densityCells(dimension, cellBlocks);
        TreeMap<Long, Integer> weights = new TreeMap<>();
        boolean any = false;
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            int own = entry.getValue() == null ? 0 : entry.getValue();
            if (own <= 0) {
                continue;
            }
            any = true;
            int cx = unpackX(entry.getKey());
            int cz = unpackZ(entry.getKey());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int add = dx == 0 && dz == 0 ? 2 * own : own;
                    weights.merge(pack(cx + dx, cz + dz), add, Integer::sum);
                }
            }
        }
        if (!any) {
            Optional<BlockPos> spawn = world.spawnPosition(dimension);
            if (spawn.isPresent()) {
                long centre = cellOf(spawn.get().getX(), spawn.get().getZ(), cellBlocks);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        weights.put(pack(unpackX(centre) + dx, unpackZ(centre) + dz), 1);
                    }
                }
            }
        }
        return new DensityCells(cellBlocks, weights);
    }

    public int cellBlocks() {
        return cellBlocks;
    }

    /** Candidate cells and their weights, in key order (deterministic). */
    public TreeMap<Long, Integer> weights() {
        return weights;
    }

    public boolean isEmpty() {
        return weights.isEmpty();
    }

    public int weight(long cell) {
        return weights.getOrDefault(cell, 0);
    }
}
