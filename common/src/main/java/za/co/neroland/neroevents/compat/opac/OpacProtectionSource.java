package za.co.neroland.neroevents.compat.opac;

import java.util.function.IntSupplier;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;
import za.co.neroland.neroevents.protect.ProtectionSource;
import za.co.neroland.neroevents.protect.Verdict;

/**
 * Open Parties and Claims (order 300, id {@code opac}): an event never touches a claimed chunk. Every chunk the
 * footprint touches — grown by the protected-namespace margin, so an effect stops short of a claim's edge — is asked
 * once per evaluation; any claimed chunk denies. OPAC failing (a throw from the lookup) propagates, so the evaluation
 * marks this source failing and denies: a claim mod that cannot answer protects everything.
 */
public final class OpacProtectionSource implements ProtectionSource {

    public static final String ID = "opac";

    /** Whether a chunk is claimed by anyone. Throws on any OPAC failure. */
    @FunctionalInterface
    public interface ChunkClaimLookup {
        boolean isChunkClaimed(Identifier dimension, int chunkX, int chunkZ);
    }

    private record ChunkKey(Identifier dimension, int chunkX, int chunkZ) {
    }

    private final ChunkClaimLookup lookup;
    private final IntSupplier marginBlocks;

    public OpacProtectionSource(ChunkClaimLookup lookup, IntSupplier marginBlocks) {
        this.lookup = lookup;
        this.marginBlocks = marginBlocks;
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
        for (Footprint.ChunkXZ chunk : footprint.grow(Math.max(0, marginBlocks.getAsInt())).chunkCoords()) {
            Boolean claimed = ctx.cached(new ChunkKey(dimension, chunk.x(), chunk.z()),
                    () -> lookup.isChunkClaimed(dimension, chunk.x(), chunk.z()));
            if (Boolean.TRUE.equals(claimed)) {
                ctx.note("claimed chunk");
                return Verdict.DENY;
            }
        }
        return Verdict.ABSTAIN;
    }
}
