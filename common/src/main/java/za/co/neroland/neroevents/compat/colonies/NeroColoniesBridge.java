package za.co.neroland.neroevents.compat.colonies;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.nerocolonies.colony.ColonyApi;

/**
 * The <b>only</b> class in NeroEvents that imports {@code za.co.neroland.nerocolonies}. NeroColonies is compiled
 * {@code compileOnly} and is optional: this class is listed in {@code META-INF/services}, instantiated by
 * {@link ColoniesCompat} only after {@code isModLoaded("nerocolonies")}, and left out of a build with
 * {@code nerocolonies_compat=false}. Boolean-only {@code ColonyApi.isClaimed}; anything NeroColonies throws
 * propagates so the protection source fails closed.
 */
public final class NeroColoniesBridge implements ColoniesBridge {

    /** For {@link java.util.ServiceLoader}. */
    public NeroColoniesBridge() {
    }

    @Override
    public boolean isClaimed(ServerLevel level, BlockPos pos) {
        return ColonyApi.isClaimed(level, pos);
    }
}
