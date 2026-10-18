package za.co.neroland.neroevents.compat.nerospace;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.nerospace.api.EnvironmentSnapshot;
import za.co.neroland.nerospace.api.NerospaceEnvironment;
import za.co.neroland.nerospace.api.NerospacePlanets;
import za.co.neroland.nerospace.api.NerospaceStations;
import za.co.neroland.nerospace.api.PlanetId;
import za.co.neroland.nerospace.api.StationInfo;

/**
 * The <b>only</b> class in NeroEvents that imports {@code za.co.neroland.nerospace}. Nerospace is compiled
 * {@code compileOnly} and is optional: this class is listed in {@code META-INF/services}, instantiated by
 * {@link NerospaceCompat} only after {@code isModLoaded("nerospace")}, and left out of a build with
 * {@code nerospace_compat=false}. Public {@code nerospace.api} only. Anything Nerospace throws propagates; the
 * façade's guard records it.
 */
public final class NeroNerospaceBridge implements NerospaceBridge {

    /** For {@link java.util.ServiceLoader}. */
    public NeroNerospaceBridge() {
    }

    @Override
    public Optional<Identifier> planetId(Identifier dimension) {
        return NerospacePlanets.byDimension(ResourceKey.create(Registries.DIMENSION, dimension)).map(PlanetId::id);
    }

    @Override
    public Optional<String> atmosphereAt(ServerLevel level, BlockPos pos) {
        EnvironmentSnapshot snapshot = NerospaceEnvironment.at(level, pos);
        if (!snapshot.loaded()) {
            return Optional.empty();
        }
        return Optional.of(snapshot.atmosphere().name());
    }

    @Override
    public List<BlockPos> stationPositions(MinecraftServer server) {
        List<BlockPos> out = new ArrayList<>();
        for (StationInfo station : NerospaceStations.all(server)) {
            out.add(station.position());
        }
        return List.copyOf(out);
    }
}
