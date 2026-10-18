package za.co.neroland.neroevents.testing.compat;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.neroevents.compat.opac.OpacBridge;

/** An Open Parties and Claims stand-in with a set of claimed chunks; or a bridge that fails like an API change. */
public final class FakeOpacBridge implements OpacBridge {

    public final Set<Long> claimed = new HashSet<>();
    public boolean throwing;
    public int calls;

    public FakeOpacBridge claim(int chunkX, int chunkZ) {
        claimed.add(pack(chunkX, chunkZ));
        return this;
    }

    @Override
    public boolean isChunkClaimed(MinecraftServer server, Identifier dimension, int chunkX, int chunkZ) {
        calls++;
        if (throwing) {
            throw new NoClassDefFoundError("xaero/pac/common/server/api/OpenPACServerAPI");
        }
        return claimed.contains(pack(chunkX, chunkZ));
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
