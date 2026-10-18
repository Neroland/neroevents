package za.co.neroland.neroevents.protect;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;

/**
 * The volume an effect may touch, stored as an inclusive bounding box. A cylinder is kept as its box
 * (protection is conservative). {@link #dimension(Identifier)} is the "whole dimension" sentinel: no
 * source can pre-scan it, so every source abstains and the payload checks each concrete position.
 */
public record Footprint(Identifier dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** The sentinel's coordinate span. */
    private static final int WHOLE = 30_000_000;

    public Footprint {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Footprint bounds are inverted");
        }
    }

    public static Footprint point(Identifier dimension, BlockPos pos) {
        return new Footprint(dimension, pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    /** A cylinder of {@code radius} around {@code centre} from {@code minY} to {@code maxY}, as its box. */
    public static Footprint cylinder(Identifier dimension, BlockPos centre, int radius, int minY, int maxY) {
        int r = Math.max(0, radius);
        return new Footprint(dimension, centre.getX() - r, Math.min(minY, maxY), centre.getZ() - r,
                centre.getX() + r, Math.max(minY, maxY), centre.getZ() + r);
    }

    /** The whole-dimension sentinel. */
    public static Footprint dimension(Identifier dimension) {
        return new Footprint(dimension, -WHOLE, -WHOLE, -WHOLE, WHOLE, WHOLE, WHOLE);
    }

    public boolean wholeDimension() {
        return minX <= -WHOLE && maxX >= WHOLE && minZ <= -WHOLE && maxZ >= WHOLE;
    }

    /** The box grown by {@code blocks} horizontally and vertically (the sentinel stays the sentinel). */
    public Footprint grow(int blocks) {
        if (wholeDimension() || blocks <= 0) {
            return this;
        }
        return new Footprint(dimension, minX - blocks, minY - blocks, minZ - blocks, maxX + blocks, maxY + blocks,
                maxZ + blocks);
    }

    public boolean isPoint() {
        return minX == maxX && minY == maxY && minZ == maxZ;
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Whether the XZ box overlaps the given inclusive XZ box. */
    public boolean intersectsXZ(int otherMinX, int otherMinZ, int otherMaxX, int otherMaxZ) {
        return minX <= otherMaxX && maxX >= otherMinX && minZ <= otherMaxZ && maxZ >= otherMinZ;
    }

    /** A chunk coordinate pair (bootstrap-free; {@code ChunkPos} needs registries on 26.1.2). */
    public record ChunkXZ(int x, int z) {
    }

    /**
     * Every chunk the box touches, as plain coordinate pairs. Engine code iterates these: on 26.1.2
     * {@link ChunkPos}'s class initialiser touches the chunk-status registry, so it cannot be loaded on a
     * plain JVM. Empty for the sentinel.
     */
    public List<ChunkXZ> chunkCoords() {
        if (wholeDimension()) {
            return List.of();
        }
        List<ChunkXZ> out = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                out.add(new ChunkXZ(cx, cz));
            }
        }
        return out;
    }

    /** Every chunk the box touches. Never call on the sentinel (empty there). */
    public Iterable<ChunkPos> chunks() {
        if (wholeDimension()) {
            return List.of();
        }
        int cx0 = minX >> 4;
        int cx1 = maxX >> 4;
        int cz0 = minZ >> 4;
        int cz1 = maxZ >> 4;
        List<ChunkPos> out = new ArrayList<>((cx1 - cx0 + 1) * (cz1 - cz0 + 1));
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                out.add(new ChunkPos(cx, cz));
            }
        }
        return out;
    }
}
