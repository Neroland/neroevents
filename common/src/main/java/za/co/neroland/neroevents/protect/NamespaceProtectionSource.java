package za.co.neroland.neroevents.protect;

import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The interim guard for Neroland security and colony blocks: an effect is denied when any chunk its
 * footprint (grown by {@code protectedNamespaceMarginBlocks}) touches holds a block entity from a
 * listed mod namespace ({@code protectedBlockNamespaces}), or is not loaded at all.
 *
 * <p>This walks block-entity maps of <b>loaded</b> chunks only and never loads or generates a chunk; an
 * unloaded chunk in the way fails closed. It is a registry-id check, not a proximity heuristic, and it
 * is partial by design: an unclaimed build of ordinary blocks is not recognised. The answer is memoised
 * per footprint for the evaluation's lifetime.
 */
public final class NamespaceProtectionSource implements ProtectionSource {

    public static final String ID = "namespace";

    private final Supplier<Set<String>> namespaces;
    private final IntSupplier margin;

    public NamespaceProtectionSource(Supplier<Set<String>> namespaces, IntSupplier margin) {
        this.namespaces = namespaces;
        this.margin = margin;
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
        Footprint grown = footprint.grow(margin.getAsInt());
        String denial = ctx.cached(new Key(grown), () -> scan(ctx, grown));
        if (denial.isEmpty()) {
            return Verdict.ABSTAIN;
        }
        ctx.note(denial);
        return Verdict.DENY;
    }

    /** "" when clear, otherwise the reason. */
    private String scan(ProtectionEvaluation ctx, Footprint grown) {
        Set<String> listed = namespaces.get();
        for (Footprint.ChunkXZ chunk : grown.chunkCoords()) {
            if (!ctx.world().isChunkLoaded(grown.dimension(), chunk.x(), chunk.z())) {
                return "unloaded chunk";
            }
            if (listed.isEmpty()) {
                continue;
            }
            for (String namespace : ctx.world().blockEntityNamespaces(grown.dimension(), chunk.x(), chunk.z())) {
                if (listed.contains(namespace)) {
                    return namespace;
                }
            }
        }
        return "";
    }

    private record Key(Footprint footprint) {
    }
}
