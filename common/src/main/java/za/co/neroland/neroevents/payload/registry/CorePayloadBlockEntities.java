package za.co.neroland.neroevents.payload.registry;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.payload.debris.SalvageCrateBlockEntity;
import za.co.neroland.neroevents.payload.meteor.CraterPocketBlockEntity;

/**
 * Block-entity types of the Core-only payloads, through Neroland Core's {@link RegistrationProvider}.
 *
 * <p><b>Fabric 26.1.2:</b> the two-argument {@code BlockEntityType} constructor is private there (public in
 * 26.2), which is why {@code fabric/src/main/resources/neroevents.accesswidener} widens it.
 */
public final class CorePayloadBlockEntities {

    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroEventsCommon.MOD_ID);

    public static final RegistryEntry<BlockEntityType<CraterPocketBlockEntity>> CRATER_POCKET =
            BLOCK_ENTITIES.register("crater_pocket",
                    key -> new BlockEntityType<>(CraterPocketBlockEntity::new,
                            Set.of(CorePayloadBlocks.CRATER_POCKET.get())));

    public static final RegistryEntry<BlockEntityType<SalvageCrateBlockEntity>> SALVAGE_CRATE =
            BLOCK_ENTITIES.register("salvage_crate",
                    key -> new BlockEntityType<>(SalvageCrateBlockEntity::new,
                            Set.of(CorePayloadBlocks.SALVAGE_CRATE.get())));

    private CorePayloadBlockEntities() {
    }

    /** Classload-forcing no-op (after {@link CorePayloadBlocks#init()}). */
    public static void init() {
    }
}
