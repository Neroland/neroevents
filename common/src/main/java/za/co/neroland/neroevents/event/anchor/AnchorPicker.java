package za.co.neroland.neroevents.event.anchor;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.world.DensityCells;
import za.co.neroland.neroevents.event.world.WorldView;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.protect.ProtectionResult;

/**
 * The deterministic, protection-aware anchor picker. It rolls a density cell by weight, then tries
 * uniformly random columns inside it; each column must be in a loaded chunk, have a surface, and clear
 * the protection query for the payload's whole footprint, and no player may stand within the stand-off
 * distance of it (a count from the world view, never a position). Spawn protection, exclusion zones, the station
 * dimension and the namespace guard all apply because they are protection sources.
 *
 * <p>Guarantees: never reads a player position (the world view has none), never loads a chunk, and the
 * same seed gives the same anchor.
 */
public final class AnchorPicker {

    private AnchorPicker() {
    }

    /** The picker's answer. */
    public sealed interface Result permits Found, Empty {
    }

    /** An anchor and the cell it came from. */
    public record Found(BlockPos anchor, long cell) implements Result {
    }

    /** No anchor; {@code deniedBy} names the last refusing protection source, if one refused. */
    public record Empty(String reason, @Nullable String deniedBy, @Nullable String detail) implements Result {
    }

    public static Result pick(WorldView world, ProtectionQuery protection, Identifier dimension,
            Function<BlockPos, Footprint> footprint, long seed, int cellBlocks, int attemptsPerCell,
            int maxCellRolls, int minPlayerDistanceBlocks) {
        Random rng = new Random(seed);
        DensityCells density = DensityCells.of(world, dimension, cellBlocks);
        if (density.isEmpty()) {
            return new Empty("no_anchor", null, null);
        }
        TreeMap<Long, Integer> cells = new TreeMap<>(density.weights());
        String deniedBy = null;
        String detail = null;
        for (int roll = 0; roll < maxCellRolls && !cells.isEmpty(); roll++) {
            long cell = weightedChoice(cells, rng);
            cells.remove(cell);
            int minX = DensityCells.unpackX(cell) * cellBlocks;
            int minZ = DensityCells.unpackZ(cell) * cellBlocks;
            for (int attempt = 0; attempt < attemptsPerCell; attempt++) {
                int x = minX + rng.nextInt(cellBlocks);
                int z = minZ + rng.nextInt(cellBlocks);
                if (!world.isChunkLoaded(dimension, x >> 4, z >> 4)) {
                    continue;
                }
                OptionalInt y = world.surfaceY(dimension, x, z);
                if (y.isEmpty()) {
                    continue;
                }
                if (minPlayerDistanceBlocks > 0
                        && world.playersWithinBlocks(dimension, x, z, minPlayerDistanceBlocks) > 0) {
                    continue;
                }
                BlockPos anchor = new BlockPos(x, y.getAsInt(), z);
                ProtectionResult result = protection.evaluation(world).mayAffectAll(footprint.apply(anchor));
                if (result.allowed()) {
                    return new Found(anchor, cell);
                }
                deniedBy = result.deniedBy();
                detail = result.detail();
            }
        }
        return new Empty("no_anchor", deniedBy, detail);
    }

    private static long weightedChoice(TreeMap<Long, Integer> cells, Random rng) {
        long total = 0L;
        for (int weight : cells.values()) {
            total += Math.max(0, weight);
        }
        if (total <= 0L) {
            return cells.firstKey();
        }
        long r = (long) (rng.nextDouble() * total);
        for (Map.Entry<Long, Integer> entry : cells.entrySet()) {
            r -= Math.max(0, entry.getValue());
            if (r < 0) {
                return entry.getKey();
            }
        }
        return cells.lastKey();
    }
}
