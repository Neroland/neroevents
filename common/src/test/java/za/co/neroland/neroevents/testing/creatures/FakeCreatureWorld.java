package za.co.neroland.neroevents.testing.creatures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.compat.creatures.CreaturesBridge.SummonedBoss;
import za.co.neroland.neroevents.creatures.CreatureWorld;
import za.co.neroland.neroevents.payload.PendingCredits;
import za.co.neroland.neroevents.protect.Footprint;

/** A scriptable {@link CreatureWorld}: a fake NeroCreatures (waves and bosses) plus recorded credits. */
public final class FakeCreatureWorld implements CreatureWorld {

    /** An opaque wave handle. */
    public record Handle(UUID waveId, int budget) {
    }

    // --- waves
    public final List<Integer> reserved = new ArrayList<>();
    public final List<Handle> closed = new ArrayList<>();
    public final List<UUID> swept = new ArrayList<>();
    public final Map<UUID, BlockPos> spawned = new LinkedHashMap<>();
    public final List<Identifier> spawnedTypes = new ArrayList<>();
    public int spawnCalls;
    /** When false, {@code spawnWithin} refuses every mob. */
    public boolean spawnAccepts = true;
    public final Set<UUID> gone = new HashSet<>();
    public final Map<UUID, List<UUID>> waves = new HashMap<>();

    // --- players and credits
    public final Map<UUID, BlockPos> players = new LinkedHashMap<>();
    public final List<String> credits = new ArrayList<>();
    public Footprint lastBounds;

    // --- boss
    public Set<Identifier> bossIds = new HashSet<>();
    public Optional<SummonedBoss> summonResult = Optional.empty();
    public final Map<UUID, Optional<Boolean>> defeated = new HashMap<>();
    public final Set<UUID> activeFights = new HashSet<>();
    public final List<UUID> discarded = new ArrayList<>();
    public int defeatedChecks;
    public int summonCalls;

    @Override
    public void resetIndex() {
    }

    @Override
    public int drain(PendingCredits pending) {
        return pending.drainInto(uuid -> uuid, new PendingCredits.Sink<UUID>() {
            @Override
            public boolean loot(UUID player, String onceKey) {
                credits.add("loot " + player + " " + onceKey);
                return true;
            }

            @Override
            public boolean supplyClaim(UUID player, UUID podUuid) {
                credits.add("claim " + player);
                return true;
            }

            @Override
            public boolean objective(UUID player, double fraction) {
                credits.add("objective " + player);
                return true;
            }

            @Override
            public boolean survival(UUID player, int waveIndex) {
                credits.add("survival " + player + " " + waveIndex);
                return true;
            }

            @Override
            public boolean damage(net.minecraft.world.damagesource.DamageSource source, float amount) {
                credits.add("damage " + amount);
                return true;
            }
        });
    }

    @Override
    public boolean isGone(UUID entity) {
        return gone.contains(entity);
    }

    @Override
    public List<UUID> playersInBounds(Footprint bounds) {
        lastBounds = bounds;
        List<UUID> out = new ArrayList<>();
        players.forEach((id, pos) -> {
            if (bounds.contains(pos.getX(), pos.getZ())) {
                out.add(id);
            }
        });
        return out;
    }

    @Override
    public @Nullable Object reserveWave(int budget) {
        reserved.add(budget);
        return new Handle(UUID.randomUUID(), budget);
    }

    @Override
    public @Nullable UUID waveId(Object handle) {
        return ((Handle) handle).waveId();
    }

    @Override
    public @Nullable UUID spawnWithin(Object handle, Identifier entityType, BlockPos pos) {
        spawnCalls++;
        if (!spawnAccepts) {
            return null;
        }
        UUID id = UUID.randomUUID();
        spawned.put(id, pos);
        spawnedTypes.add(entityType);
        waves.computeIfAbsent(((Handle) handle).waveId(), k -> new ArrayList<>()).add(id);
        return id;
    }

    @Override
    public void closeWave(Object handle) {
        closed.add((Handle) handle);
    }

    @Override
    public void sweepWave(UUID waveId) {
        swept.add(waveId);
    }

    @Override
    public List<UUID> findWave(UUID waveId) {
        List<UUID> out = new ArrayList<>();
        for (UUID mob : waves.getOrDefault(waveId, List.of())) {
            if (!gone.contains(mob)) {
                out.add(mob);
            }
        }
        return out;
    }

    @Override
    public Set<Identifier> bossIds() {
        return bossIds;
    }

    @Override
    public Optional<SummonedBoss> summon(BlockPos pos, Identifier bossId) {
        summonCalls++;
        return summonResult;
    }

    @Override
    public Optional<Boolean> isDefeated(UUID entity) {
        defeatedChecks++;
        return defeated.getOrDefault(entity, Optional.empty());
    }

    @Override
    public boolean isFightActive(UUID fightId) {
        return activeFights.contains(fightId);
    }

    @Override
    public void discard(UUID entity) {
        discarded.add(entity);
    }
}
