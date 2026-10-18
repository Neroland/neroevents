package za.co.neroland.neroevents.compat.nerospace;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * What NeroEvents needs from Nerospace. A pure interface: it imports nothing from Nerospace, so every
 * caller compiles and runs without it. The one implementation that does import Nerospace is loaded by
 * {@link java.util.ServiceLoader} only when Nerospace is present; callers go through the façade, which
 * catches anything a method throws.
 *
 * <p>Every method <b>may throw</b> (a sibling API change surfaces as a {@code RuntimeException} or a
 * {@code LinkageError}).
 */
public interface NerospaceBridge {

    /**
     * The planet a dimension belongs to, if any ({@code NerospacePlanets.byDimension(...)} mapped to the
     * planet id). May throw.
     */
    Optional<Identifier> planetId(Identifier dimension);

    /**
     * Atmosphere name: {@code "VACUUM" | "PRESSURIZED" | "TERRAFORMING" | "BREATHABLE"}; empty if the chunk
     * is unloaded ({@code snapshot.loaded() == false}). From {@code NerospaceEnvironment.at(level, pos)}.
     * May throw.
     */
    Optional<String> atmosphereAt(ServerLevel level, BlockPos pos);

    /**
     * Every station position ({@code NerospaceStations.all(server)} mapped to {@code StationInfo::position}).
     * Kept for the raid-target seam; unused in 0.5.0. May throw.
     */
    List<BlockPos> stationPositions(MinecraftServer server);
}
