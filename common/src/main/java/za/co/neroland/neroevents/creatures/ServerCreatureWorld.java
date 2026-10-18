package za.co.neroland.neroevents.creatures;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.compat.creatures.CreaturesBridge;
import za.co.neroland.neroevents.compat.creatures.CreaturesBridge.SummonedBoss;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.ServerPayloadContext;
import za.co.neroland.neroevents.participation.Contributions;
import za.co.neroland.neroevents.payload.EventEntityIndex;
import za.co.neroland.neroevents.payload.PendingCredits;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The running-server {@link CreatureWorld}: the instance's level plus the NeroCreatures bridge. Every bridge
 * call is wrapped in {@code catch (RuntimeException | LinkageError)}: the failure goes to telemetry under
 * {@code source} with operation {@code bridge}, and the method answers its safe default.
 */
public final class ServerCreatureWorld implements CreatureWorld {

    private final ServerPayloadContext ctx;
    private final ServerLevel level;
    private final CreaturesBridge bridge;
    private final String source;

    private ServerCreatureWorld(ServerPayloadContext ctx, ServerLevel level, CreaturesBridge bridge, String source) {
        this.ctx = ctx;
        this.level = level;
        this.bridge = bridge;
        this.source = source;
    }

    /**
     * The production factory: a world over the context's level, or {@code null} when the context is not a
     * running server's or the dimension is not loaded.
     */
    public static Function<PayloadContext, CreatureWorld> factory(CreaturesBridge bridge, String telemetrySource) {
        return context -> {
            if (bridge == null || !(context instanceof ServerPayloadContext s)) {
                return null;
            }
            ServerLevel level = s.level();
            return level == null ? null : new ServerCreatureWorld(s, level, bridge, telemetrySource);
        };
    }

    private void failed(Throwable failure) {
        NeroEventsTelemetry.captureHandledException(failure, source, "bridge");
    }

    @Override
    public void resetIndex() {
        EventEntityIndex.resetForServer(ctx.server());
    }

    @Override
    public int drain(PendingCredits pending) {
        return pending.drainInto(ctx);
    }

    @Override
    public boolean isGone(UUID entity) {
        Entity e = entity == null ? null : level.getEntity(entity);
        return e == null || e.isRemoved() || !e.isAlive();
    }

    @Override
    public List<UUID> playersInBounds(Footprint bounds) {
        List<UUID> out = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (bounds.contains(player.getBlockX(), player.getBlockZ())
                    && Contributions.attributable(ctx.server(), player)) {
                out.add(player.getUUID());
            }
        }
        return out;
    }

    @Override
    public @Nullable Object reserveWave(int budget) {
        try {
            return bridge.reserveWave(level, budget);
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
            return null;
        }
    }

    @Override
    public @Nullable UUID waveId(Object handle) {
        try {
            return bridge.waveId(handle);
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
            return null;
        }
    }

    @Override
    public @Nullable UUID spawnWithin(Object handle, Identifier entityType, BlockPos pos) {
        try {
            return bridge.spawnWithin(handle, entityType, pos);
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
            return null;
        }
    }

    @Override
    public void closeWave(Object handle) {
        try {
            bridge.closeWave(handle);
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
        }
    }

    @Override
    public void sweepWave(UUID waveId) {
        try {
            bridge.sweepWave(level, waveId);
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
        }
    }

    @Override
    public List<UUID> findWave(UUID waveId) {
        try {
            List<UUID> found = bridge.findWave(level, waveId);
            return found == null ? List.of() : List.copyOf(found);
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
            return List.of();
        }
    }

    @Override
    public Set<Identifier> bossIds() {
        try {
            Set<Identifier> ids = bridge.bossIds();
            return ids == null ? Set.of() : Set.copyOf(ids);
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
            return Set.of();
        }
    }

    @Override
    public Optional<SummonedBoss> summon(BlockPos pos, Identifier bossId) {
        try {
            Optional<SummonedBoss> summoned = bridge.summon(level, pos, bossId);
            return summoned == null ? Optional.empty() : summoned;
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Boolean> isDefeated(UUID entity) {
        try {
            Optional<Boolean> defeated = bridge.isDefeated(level, entity);
            return defeated == null ? Optional.empty() : defeated;
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
            return Optional.empty();
        }
    }

    @Override
    public boolean isFightActive(UUID fightId) {
        try {
            return bridge.isFightActive(fightId);
        } catch (RuntimeException | LinkageError failure) {
            failed(failure);
            return false;
        }
    }

    @Override
    public void discard(UUID entity) {
        Entity e = entity == null ? null : level.getEntity(entity);
        if (e != null && !e.isRemoved()) {
            e.discard();
        }
    }
}
