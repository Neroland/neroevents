package za.co.neroland.neroevents.protect;

import net.minecraft.resources.Identifier;

/**
 * Denies every effect in the Nerospace station dimension: that dimension holds nothing but player
 * builds, so events there are mob-only by construction. An id comparison, so the rule holds whether or
 * not Nerospace is installed.
 */
public final class StationProtectionSource implements ProtectionSource {

    public static final String ID = "station";

    public static final Identifier STATION_DIMENSION = Identifier.fromNamespaceAndPath("nerospace", "station");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Verdict check(ProtectionEvaluation ctx, Footprint footprint) {
        if (footprint.wholeDimension()) {
            return Verdict.ABSTAIN;
        }
        if (STATION_DIMENSION.equals(footprint.dimension())) {
            ctx.note("station dimension");
            return Verdict.DENY;
        }
        return Verdict.ABSTAIN;
    }
}
