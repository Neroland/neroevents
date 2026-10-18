package za.co.neroland.neroevents.compat.opac;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import xaero.pac.common.server.api.OpenPACServerAPI;

/**
 * Open Parties and Claims, as an {@link OpacBridge}. The <b>only</b> class in NeroEvents that imports
 * {@code xaero.pac}; compiled {@code compileOnly} against OPAC's common API jar, listed in {@code META-INF/services},
 * loaded by {@link OpacCompat} only when {@code openpartiesandclaims} is installed, and left out of a build with
 * {@code opac_compat=false}. Anything OPAC throws propagates, so the protection source fails closed.
 */
public final class XaeroOpacBridge implements OpacBridge {

    /** For {@link java.util.ServiceLoader}. */
    public XaeroOpacBridge() {
    }

    @Override
    public boolean isChunkClaimed(MinecraftServer server, Identifier dimension, int chunkX, int chunkZ) {
        if (server == null || dimension == null) {
            throw new IllegalStateException("No server to ask Open Parties and Claims");
        }
        return OpenPACServerAPI.get(server).getServerClaimsManager().get(dimension, chunkX, chunkZ) != null;
    }
}
