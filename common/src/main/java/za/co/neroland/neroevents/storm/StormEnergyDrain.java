package za.co.neroland.neroevents.storm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.platform.EnergyLookup;

import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;

/**
 * One storm drain pass (every 200 ticks while a draining storm runs). Candidates are the Neroland Core
 * energy storages in <b>loaded</b> chunks within {@code stormDrainChunkRadius} of a player in the
 * dimension: player positions choose only what the storm reaches, never who is rewarded or pushed. Each
 * candidate is checked for open sky and against the protection query before {@link DrainPlan} decides how
 * much it loses; a storage inside a claim, colony, installation margin or exclusion zone is never
 * drained. Never loads a chunk.
 *
 * <p>On Fabric Core's {@link EnergyLookup} sees Nero storages only; on NeoForge and Forge it also reaches
 * standard Forge-Energy blocks.
 */
public final class StormEnergyDrain {

    private StormEnergyDrain() {
    }

    /** Runs one pass; returns the number of storages drained. Server thread. */
    public static int pass(ServerLevel level, Identifier dimension, ProtectionEvaluation protection,
            StormVariant variant, int intensity, EngineSettings settings) {
        if (!variant.drains()) {
            return 0;
        }
        DrainPlan.Limits limits = new DrainPlan.Limits(settings.stormDrainFractionPerPass,
                settings.stormDrainMaxPerPass, settings.stormDrainFloorFraction,
                settings.stormDrainMaxStoragesPerPass);
        if (DrainPlan.fraction(variant, intensity, limits) <= 0.0D || limits.maxPerPass() <= 0L) {
            return 0;
        }
        int radius = Math.max(1, settings.stormDrainChunkRadius);
        int maxStorages = Math.max(1, limits.maxStoragesPerPass());
        Set<Long> chunksSeen = new HashSet<>();
        Set<Long> positionsSeen = new HashSet<>();
        Map<BlockPos, NeroEnergyStorage> storages = new HashMap<>();
        List<DrainPlan.Candidate> candidates = new ArrayList<>();
        outer:
        for (ServerPlayer player : level.players()) {
            ChunkPos centre = ChunkPos.containing(player.blockPosition());
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int cx = centre.x() + dx;
                    int cz = centre.z() + dz;
                    if (!chunksSeen.add(ChunkPos.pack(cx, cz))) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockPos raw : List.copyOf(chunk.getBlockEntities().keySet())) {
                        BlockPos pos = raw.immutable();
                        if (!positionsSeen.add(pos.asLong())) {
                            continue;
                        }
                        NeroEnergyStorage storage = EnergyLookup.INSTANCE.find(level, pos, null);
                        if (storage == null) {
                            continue;
                        }
                        boolean exposed = level.canSeeSky(pos.above());
                        boolean allowed = exposed && protection.mayAffect(dimension, pos).allowed();
                        candidates.add(new DrainPlan.Candidate(pos, storage.getAmount(), storage.getCapacity(),
                                exposed, allowed));
                        storages.put(pos, storage);
                        if (candidates.size() >= maxStorages) {
                            break outer;
                        }
                    }
                }
            }
        }
        int drained = 0;
        for (DrainPlan.Drain drain : DrainPlan.of(DrainPlan.cap(candidates, maxStorages), variant, intensity, limits)) {
            NeroEnergyStorage storage = storages.get(drain.pos());
            // Protection and exposure were checked when the candidate was built (allowed == true here).
            if (storage != null && storage.extract(drain.extract(), false) > 0L) {
                drained++;
            }
        }
        return drained;
    }
}
