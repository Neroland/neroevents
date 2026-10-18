package za.co.neroland.neroevents.protect;

/**
 * Seam for NeroSecurity installations. NeroSecurity has no public query API yet, so this source always
 * abstains; until it does, the namespace guard ({@link NamespaceProtectionSource}) keeps destructive
 * effects away from NeroSecurity block entities.
 *
 * <p>The API this seam is waiting for: a small public, owner-free query such as
 * {@code SecurityAreas.isProtected(ServerLevel, BlockPos)} (inside any installation's protected
 * footprint) and {@code SecurityAreas.isInLockdown(ServerLevel, BlockPos)}, optionally
 * {@code protectedBoxes(MinecraftServer, dimension)} for footprint pre-checks — with no owner,
 * member, installation id or name in any return value. Once it ships in a published release, this class
 * becomes a {@code compileOnly} + {@code isModLoaded} façade call and nothing else changes.
 */
public final class SecurityProtectionSource implements ProtectionSource {

    public static final String ID = "security";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Verdict check(ProtectionEvaluation ctx, Footprint footprint) {
        return Verdict.ABSTAIN;
    }
}
