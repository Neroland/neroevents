package za.co.neroland.neroevents.compat.creatures;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTypeTest;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.boss.BossController;
import za.co.neroland.nerocreatures.boss.BossFights;
import za.co.neroland.nerocreatures.boss.BossSummons;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.boss.NeroBossEntity;
import za.co.neroland.nerocreatures.spawn.InvasionBudget;

/**
 * The <b>only</b> class in NeroEvents that imports {@code za.co.neroland.nerocreatures}. NeroCreatures is compiled
 * {@code compileOnly} and is optional: this class is listed in {@code META-INF/services}, instantiated by
 * {@link CreaturesCompat} only after {@code isModLoaded("nerocreatures")}, and left out of a build with
 * {@code nerocreatures_compat=false}. Uses the public summon and invasion seams only ({@code BossSummons},
 * {@code BossFights}, {@code InvasionBudget}); wave handles leave this class as opaque objects. Anything NeroCreatures
 * throws propagates; the façade's guard records it.
 */
public final class NeroCreaturesBridge implements CreaturesBridge {

    /** Upper bound on the entities one wave lookup looks at (NeroCreatures' own sweep limit). */
    private static final int FIND_LIMIT = 512;

    /** For {@link java.util.ServiceLoader}. */
    public NeroCreaturesBridge() {
    }

    @Override
    public Set<Identifier> bossIds() {
        return BossSummons.bossIds();
    }

    @Override
    public Optional<SummonedBoss> summon(ServerLevel level, BlockPos pos, Identifier bossId) {
        return BossSummons.summon(level, pos, bossId, null)
                .map(boss -> new SummonedBoss(boss.getUUID(), boss.bossController().fightId()));
    }

    @Override
    public boolean isFightActive(UUID fightId) {
        for (BossController controller : BossFights.active()) {
            if (fightId.equals(controller.fightId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Boolean> isDefeated(ServerLevel level, UUID entityUuid) {
        Entity entity = level.getEntity(entityUuid);
        if (entity instanceof NeroBossEntity boss) {
            return Optional.of(boss.bossController().isDefeated());
        }
        return Optional.empty();
    }

    @Override
    public Object reserveWave(ServerLevel level, int budget) {
        return InvasionBudget.reserve(level, budget);
    }

    @Override
    public UUID waveId(Object handle) {
        return ((InvasionBudget.Handle) handle).waveId();
    }

    @Override
    public int placed(Object handle) {
        return ((InvasionBudget.Handle) handle).placed();
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable UUID spawnWithin(Object handle, Identifier entityType, BlockPos pos) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityType);
        if (type.isEmpty()) {
            return null;
        }
        try {
            Mob mob = InvasionBudget.spawnWithin((InvasionBudget.Handle) handle,
                    (EntityType<? extends Mob>) type.get(), pos);
            return mob == null ? null : mob.getUUID();
        } catch (ClassCastException notAMob) {
            return null; // a wave lists mobs only; a non-mob type places nothing
        }
    }

    @Override
    public int closeWave(Object handle) {
        return InvasionBudget.close((InvasionBudget.Handle) handle);
    }

    @Override
    public int sweepWave(ServerLevel level, UUID waveId) {
        return InvasionBudget.sweep(level, waveId);
    }

    @Override
    public List<UUID> findWave(ServerLevel level, UUID waveId) {
        List<NeroCreatureEntity> found = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(NeroCreatureEntity.class),
                creature -> waveId.equals(creature.waveId()), found, FIND_LIMIT);
        List<UUID> out = new ArrayList<>(found.size());
        for (NeroCreatureEntity creature : found) {
            out.add(creature.getUUID());
        }
        return List.copyOf(out);
    }
}
