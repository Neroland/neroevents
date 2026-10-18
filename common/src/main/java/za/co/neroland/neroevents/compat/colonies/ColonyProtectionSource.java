package za.co.neroland.neroevents.compat.colonies;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;
import za.co.neroland.neroevents.protect.ProtectionSource;
import za.co.neroland.neroevents.protect.Verdict;

/**
 * NeroColonies claims (order 400, id {@code colony}): an event never touches a block a colony claims. Asks
 * NeroColonies' boolean-only {@code isClaimed} — never {@code canBuild}, which is a per-player question an event has
 * no player for.
 *
 * <p>A point asks once. A box asks once per chunk it touches (at the chunk centre, memoised per evaluation)
 * <b>and</b> at the box's four corners: colony claims are radius-based rather than chunk-aligned, so a claim that
 * only clips a corner would otherwise slip through. Any claimed answer denies. A lookup that cannot answer
 * ({@code null}: the dimension is not loaded on this server) denies too, noted {@code dimension not loaded}. A
 * lookup that throws propagates, so the evaluation marks this source failing and denies.
 */
public final class ColonyProtectionSource implements ProtectionSource {

    public static final String ID = "colony";

    /**
     * Whether a colony claims a position; {@code null} = cannot answer: the dimension is not loaded (treated as
     * claimed). May throw.
     */
    @FunctionalInterface
    public interface ClaimLookup {
        @Nullable Boolean isClaimed(Identifier dimension, BlockPos pos);
    }

    private record ChunkKey(Identifier dimension, int chunkX, int chunkZ) {
    }

    private final ClaimLookup lookup;

    public ColonyProtectionSource(ClaimLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Verdict check(ProtectionEvaluation ctx, Footprint footprint) {
        if (footprint.wholeDimension()) {
            return Verdict.ABSTAIN;
        }
        Identifier dimension = footprint.dimension();
        if (footprint.isPoint()) {
            return claimed(ctx, dimension, new BlockPos(footprint.minX(), footprint.minY(), footprint.minZ()));
        }
        for (Footprint.ChunkXZ chunk : footprint.chunkCoords()) {
            int x = (chunk.x() << 4) + 8;
            int z = (chunk.z() << 4) + 8;
            int y = ctx.world().isChunkLoaded(dimension, chunk.x(), chunk.z())
                    ? ctx.world().surfaceY(dimension, x, z).orElse(footprint.minY())
                    : footprint.minY();
            BlockPos centre = new BlockPos(x, y, z);
            Boolean hit = ctx.cached(new ChunkKey(dimension, chunk.x(), chunk.z()),
                    () -> lookup.isClaimed(dimension, centre));
            if (hit == null) {
                ctx.note("dimension not loaded");
                return Verdict.DENY;
            }
            if (hit) {
                ctx.note("colony claim");
                return Verdict.DENY;
            }
        }
        int y = footprint.minY();
        int[][] corners = {
                {footprint.minX(), footprint.minZ()}, {footprint.minX(), footprint.maxZ()},
                {footprint.maxX(), footprint.minZ()}, {footprint.maxX(), footprint.maxZ()}};
        for (int[] corner : corners) {
            if (claimed(ctx, dimension, new BlockPos(corner[0], y, corner[1])) == Verdict.DENY) {
                return Verdict.DENY;
            }
        }
        return Verdict.ABSTAIN;
    }

    private Verdict claimed(ProtectionEvaluation ctx, Identifier dimension, BlockPos pos) {
        Boolean hit = lookup.isClaimed(dimension, pos);
        if (hit == null) {
            ctx.note("dimension not loaded");
            return Verdict.DENY;
        }
        if (hit) {
            ctx.note("colony claim");
            return Verdict.DENY;
        }
        return Verdict.ABSTAIN;
    }
}
