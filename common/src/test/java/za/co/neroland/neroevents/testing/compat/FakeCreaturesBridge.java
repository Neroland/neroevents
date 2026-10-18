package za.co.neroland.neroevents.testing.compat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.neroevents.compat.creatures.CreaturesBridge;

/** A NeroCreatures stand-in: one boss id, no summons, waves that place nothing; or one that fails. */
public final class FakeCreaturesBridge implements CreaturesBridge {

    public static final Identifier CINDER_TYRANT = Identifier.fromNamespaceAndPath("nerocreatures", "cinder_tyrant");

    public boolean throwing;
    public int calls;

    private void call() {
        calls++;
        if (throwing) {
            throw new NoClassDefFoundError("za/co/neroland/nerocreatures/boss/BossSummons");
        }
    }

    @Override
    public Set<Identifier> bossIds() {
        call();
        return Set.of(CINDER_TYRANT);
    }

    @Override
    public Optional<SummonedBoss> summon(ServerLevel level, BlockPos pos, Identifier bossId) {
        call();
        return Optional.empty();
    }

    @Override
    public boolean isFightActive(UUID fightId) {
        call();
        return false;
    }

    @Override
    public Optional<Boolean> isDefeated(ServerLevel level, UUID entityUuid) {
        call();
        return Optional.empty();
    }

    @Override
    public Object reserveWave(ServerLevel level, int budget) {
        call();
        return new Object();
    }

    @Override
    public UUID waveId(Object handle) {
        call();
        return new UUID(0L, 1L);
    }

    @Override
    public int placed(Object handle) {
        call();
        return 0;
    }

    @Override
    public @Nullable UUID spawnWithin(Object handle, Identifier entityType, BlockPos pos) {
        call();
        return null;
    }

    @Override
    public int closeWave(Object handle) {
        call();
        return 0;
    }

    @Override
    public int sweepWave(ServerLevel level, UUID waveId) {
        call();
        return 0;
    }

    @Override
    public List<UUID> findWave(ServerLevel level, UUID waveId) {
        call();
        return List.of();
    }
}
