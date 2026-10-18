package za.co.neroland.neroevents.creatures;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.compat.creatures.CreaturesBridge.SummonedBoss;
import za.co.neroland.neroevents.payload.PendingCredits;
import za.co.neroland.neroevents.protect.Footprint;

/**
 * The level-bound part of the wave and boss payloads, behind one seam: the production adapter
 * ({@link ServerCreatureWorld}) wraps the instance's {@code ServerLevel}, the NeroCreatures bridge and the
 * {@code Contributions} drain; tests pass a fake. One is made per payload call from the payload context.
 *
 * <p>Every bridge-backed method is fail-safe in production: a sibling failure is reported to telemetry and
 * answered with the safe default named on each method, so a broken NeroCreatures ends an event honestly
 * instead of crashing the tick. Player identities appear only as UUIDs handed to {@link PendingCredits}.
 */
public interface CreatureWorld {

    /** Resets {@code EventEntityIndex} when the server changed (identity check; cheap). */
    void resetIndex();

    /** Drains queued credits into the instance's contribution table; returns how many landed. */
    int drain(PendingCredits pending);

    /** Whether the entity is unloaded, removed or dead. */
    boolean isGone(UUID entity);

    /** Online, non-spectating players standing inside the bounds' XZ box (survival credit only). */
    List<UUID> playersInBounds(Footprint bounds);

    // --- waves --------------------------------------------------------------------------------

    /** {@code InvasionBudget.reserve}; {@code null} on failure. */
    @Nullable Object reserveWave(int budget);

    /** The wave's persistent id; {@code null} on failure. */
    @Nullable UUID waveId(Object handle);

    /** One mob within the wave's budget; {@code null} when nothing spawned (or on failure). */
    @Nullable UUID spawnWithin(Object handle, Identifier entityType, BlockPos pos);

    /** {@code InvasionBudget.close}: discards the wave's survivors. Failure is swallowed. */
    void closeWave(Object handle);

    /** {@code InvasionBudget.sweep}: removes loaded mobs of a past wave. Failure is swallowed. */
    void sweepWave(UUID waveId);

    /** Loaded mobs carrying the wave id (resume rebuild); empty on failure. */
    List<UUID> findWave(UUID waveId);

    // --- boss ---------------------------------------------------------------------------------

    /** Every boss id NeroCreatures can summon; empty on failure. */
    Set<Identifier> bossIds();

    /** Summons the boss; empty when refused (or on failure). */
    Optional<SummonedBoss> summon(BlockPos pos, Identifier bossId);

    /** Whether the boss is defeated; empty when it is not loaded (or on failure). */
    Optional<Boolean> isDefeated(UUID entity);

    /** Whether the fight is still running; {@code false} on failure. */
    boolean isFightActive(UUID fightId);

    /** Discards the entity if it is loaded. */
    void discard(UUID entity);
}
