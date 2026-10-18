package za.co.neroland.neroevents.compat.creatures;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/**
 * What NeroEvents needs from NeroCreatures (world bosses and invasion waves). A pure interface: it imports
 * nothing from NeroCreatures; the one implementation that does is loaded by {@link java.util.ServiceLoader}
 * only when NeroCreatures is present, and callers go through the façade, which catches anything a method
 * throws. Wave handles are opaque {@link Object}s so no NeroCreatures type leaks into callers.
 *
 * <p>Every method <b>may throw</b> (a sibling API change surfaces as a {@code RuntimeException} or a
 * {@code LinkageError}).
 */
public interface CreaturesBridge {

    /** Every boss id NeroCreatures can summon ({@code BossSummons.bossIds()}). May throw. */
    Set<Identifier> bossIds();

    /**
     * Returns the boss entity UUID and fight id, or empty when {@code BossSummons} refused
     * ({@code BossSummons.summon(level, pos, bossId, null)}). May throw.
     */
    Optional<SummonedBoss> summon(ServerLevel level, BlockPos pos, Identifier bossId);

    /** A summoned boss: its entity UUID and, when it has one, its fight id. */
    record SummonedBoss(UUID entityUuid, @Nullable UUID fightId) {
    }

    /** Whether a fight with this id is still running ({@code BossFights.active()}). May throw. */
    boolean isFightActive(UUID fightId);

    /**
     * Whether the boss entity is defeated; empty if the entity is not loaded or is not a NeroCreatures boss.
     * May throw.
     */
    Optional<Boolean> isDefeated(ServerLevel level, UUID entityUuid);

    /** Reserves a wave of {@code budget} ({@code InvasionBudget.reserve}); the handle is opaque. May throw. */
    Object reserveWave(ServerLevel level, int budget);

    /** The persistent wave id of a reserved wave. May throw. */
    UUID waveId(Object handle);

    /** How many mobs the wave has placed so far. May throw. */
    int placed(Object handle);

    /**
     * Spawns one mob of {@code entityType} at {@code pos} within the wave's budget
     * ({@code InvasionBudget.spawnWithin}); returns the mob's UUID, or {@code null} when nothing was spawned
     * (unknown type, budget spent, refused). May throw.
     */
    @Nullable
    UUID spawnWithin(Object handle, Identifier entityType, BlockPos pos);

    /** Closes the wave ({@code InvasionBudget.close}); returns the count it reports. May throw. */
    int closeWave(Object handle);

    /** Removes every loaded mob of the wave ({@code InvasionBudget.sweep}); returns how many. May throw. */
    int sweepWave(ServerLevel level, UUID waveId);

    /** Entity UUIDs of loaded NeroCreature entities carrying this wave id (resume rebuild). May throw. */
    List<UUID> findWave(ServerLevel level, UUID waveId);
}
