package za.co.neroland.neroevents.protect;

/**
 * One reason an event may not touch a place: a claim, a colony, a security installation, an exclusion
 * zone, spawn. Registered in order with {@link ProtectionSources}; the first {@link Verdict#DENY} wins.
 *
 * <p>Rules: never load a chunk; be cheap or memoise through {@link ProtectionEvaluation#cached}; abstain on
 * a {@link Footprint#wholeDimension() whole-dimension} footprint; call {@link ProtectionEvaluation#note}
 * with a short, non-identifying detail before returning DENY. A source that throws is treated as a refusal.
 */
public interface ProtectionSource {

    /** A short stable id ({@code exclusion_zones}, {@code spawn}, {@code station}, ...). */
    String id();

    Verdict check(ProtectionEvaluation ctx, Footprint footprint);
}
