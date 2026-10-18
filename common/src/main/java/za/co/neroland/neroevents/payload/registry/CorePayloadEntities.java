package za.co.neroland.neroevents.payload.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.payload.debris.DebrisEntity;
import za.co.neroland.neroevents.payload.meteor.MeteorEntity;
import za.co.neroland.neroevents.payload.supply.SupplyPodEntity;

/**
 * The Core-only payloads' entities: the supply pod, the meteor and the falling debris. Plain, AI-less
 * {@code Entity} subclasses ({@link MobCategory#MISC}, fire-immune), so no attributes are registered. Client
 * tracking ranges are in chunks: 10 (160 blocks) for the pod and the meteor, 6 (96 blocks) for debris.
 */
public final class CorePayloadEntities {

    public static final RegistrationProvider<EntityType<?>> ENTITY_TYPES =
            RegistrationProvider.get(Registries.ENTITY_TYPE, NeroEventsCommon.MOD_ID);

    public static final RegistryEntry<EntityType<SupplyPodEntity>> SUPPLY_POD = ENTITY_TYPES.register("supply_pod",
            key -> EntityType.Builder.<SupplyPodEntity>of(SupplyPodEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.5F).fireImmune().clientTrackingRange(10).updateInterval(2).build(key));

    public static final RegistryEntry<EntityType<MeteorEntity>> METEOR = ENTITY_TYPES.register("meteor",
            key -> EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC)
                    .sized(1.2F, 1.2F).fireImmune().clientTrackingRange(10).updateInterval(2).build(key));

    public static final RegistryEntry<EntityType<DebrisEntity>> DEBRIS = ENTITY_TYPES.register("debris",
            key -> EntityType.Builder.<DebrisEntity>of(DebrisEntity::new, MobCategory.MISC)
                    .sized(1.2F, 0.6F).fireImmune().clientTrackingRange(6).updateInterval(2).build(key));

    private CorePayloadEntities() {
    }

    /** Classload-forcing no-op (after the block entities). */
    public static void init() {
    }
}
