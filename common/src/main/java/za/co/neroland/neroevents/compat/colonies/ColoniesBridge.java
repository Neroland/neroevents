package za.co.neroland.neroevents.compat.colonies;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * What NeroEvents needs from NeroColonies. A pure interface: it imports nothing from NeroColonies; the one
 * implementation that does is loaded by {@link java.util.ServiceLoader} only when NeroColonies is present,
 * and callers go through the façade, which catches anything a method throws.
 *
 * <p>Every method <b>may throw</b> (a sibling API change surfaces as a {@code RuntimeException} or a
 * {@code LinkageError}).
 */
public interface ColoniesBridge {

    /** Whether a colony claims this position ({@code ColonyApi.isClaimed}). May throw. */
    boolean isClaimed(ServerLevel level, BlockPos pos);
}
