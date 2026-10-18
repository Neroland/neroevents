package za.co.neroland.neroevents.protect;

import java.util.Optional;
import java.util.function.IntSupplier;

import net.minecraft.core.BlockPos;

/**
 * Denies any footprint that comes within {@code max(server spawn protection, protectSpawnRadiusBlocks)}
 * of world spawn, measured like vanilla (the larger of the X and Z distances). Abstains in a dimension
 * without the world spawn.
 */
public final class SpawnProtectionSource implements ProtectionSource {

    public static final String ID = "spawn";

    private final IntSupplier configRadius;

    public SpawnProtectionSource(IntSupplier configRadius) {
        this.configRadius = configRadius;
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
        int radius = Math.max(ctx.world().spawnProtectionRadius(), configRadius.getAsInt());
        if (radius <= 0) {
            return Verdict.ABSTAIN;
        }
        Optional<BlockPos> spawn = ctx.world().spawnPosition(footprint.dimension());
        if (spawn.isEmpty()) {
            return Verdict.ABSTAIN;
        }
        BlockPos s = spawn.get();
        if (footprint.intersectsXZ(s.getX() - radius, s.getZ() - radius, s.getX() + radius, s.getZ() + radius)) {
            ctx.note("spawn radius " + radius);
            return Verdict.DENY;
        }
        return Verdict.ABSTAIN;
    }
}
