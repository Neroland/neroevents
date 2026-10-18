package za.co.neroland.neroevents.protect;

import java.util.List;
import java.util.function.Supplier;

import za.co.neroland.neroevents.protect.ExclusionZones.ExclusionZone;

/** Denies any footprint whose XZ box intersects an exclusion zone in the same dimension. */
public final class ExclusionZoneSource implements ProtectionSource {

    public static final String ID = "exclusion_zones";

    private final Supplier<List<ExclusionZone>> zones;

    public ExclusionZoneSource(Supplier<List<ExclusionZone>> zones) {
        this.zones = zones;
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
        for (ExclusionZone zone : zones.get()) {
            if (zone.intersects(footprint)) {
                ctx.note(zone.name());
                return Verdict.DENY;
            }
        }
        return Verdict.ABSTAIN;
    }
}
