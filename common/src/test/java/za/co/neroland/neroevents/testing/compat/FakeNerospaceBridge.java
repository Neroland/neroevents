package za.co.neroland.neroevents.testing.compat;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.neroevents.compat.nerospace.NerospaceBridge;

/** A Nerospace stand-in: no planets, no air readings, no stations; or a bridge that fails like a sibling API change. */
public final class FakeNerospaceBridge implements NerospaceBridge {

    public boolean throwing;
    public int calls;

    @Override
    public Optional<Identifier> planetId(Identifier dimension) {
        calls++;
        if (throwing) {
            throw new NoClassDefFoundError("za/co/neroland/nerospace/api/NerospacePlanets");
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> atmosphereAt(ServerLevel level, BlockPos pos) {
        calls++;
        if (throwing) {
            throw new NoClassDefFoundError("za/co/neroland/nerospace/api/NerospaceEnvironment");
        }
        return Optional.empty();
    }

    @Override
    public List<BlockPos> stationPositions(MinecraftServer server) {
        calls++;
        return List.of();
    }
}
