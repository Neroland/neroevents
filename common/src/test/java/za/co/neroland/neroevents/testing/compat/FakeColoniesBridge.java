package za.co.neroland.neroevents.testing.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.neroevents.compat.colonies.ColoniesBridge;

/** A NeroColonies stand-in: nothing is claimed; or a bridge that fails like a sibling API change. */
public final class FakeColoniesBridge implements ColoniesBridge {

    public boolean throwing;
    public int calls;

    @Override
    public boolean isClaimed(ServerLevel level, BlockPos pos) {
        calls++;
        if (throwing) {
            throw new NoClassDefFoundError("za/co/neroland/nerocolonies/colony/ColonyApi");
        }
        return false;
    }
}
