package za.co.neroland.neroevents.war;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.registry.ModCreativeTab;

/**
 * The faction war's registrations: the {@code neroevents:war_beacon} block, its block-entity type and its
 * creative-only item, through Neroland Core's {@link RegistrationProvider}. Registered in common init slot 3
 * whether or not NeroFactions is present, so worlds keep their blocks when the integration is switched off.
 *
 * <p><b>Fabric 26.1.2:</b> the two-argument {@code BlockEntityType} constructor is private there (public from
 * 26.2); {@code fabric/src/main/resources/neroevents.accesswidener} widens it.
 */
public final class WarBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroEventsCommon.MOD_ID);
    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroEventsCommon.MOD_ID);
    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroEventsCommon.MOD_ID);

    /** Unbreakable like bedrock, no loot, light 9, not occluding (the BER draws above it). */
    public static final RegistryEntry<Block> WAR_BEACON = BLOCKS.register("war_beacon",
            key -> new WarBeaconBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, 3_600_000.0F)
                    .noLootTable()
                    .lightLevel(state -> 9)
                    .noOcclusion()
                    .sound(SoundType.METAL)));

    public static final RegistryEntry<BlockEntityType<WarBeaconBlockEntity>> WAR_BEACON_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("war_beacon",
                    key -> new BlockEntityType<>(WarBeaconBlockEntity::new, Set.of(WAR_BEACON.get())));

    public static final RegistryEntry<BlockItem> WAR_BEACON_ITEM = ITEMS.register("war_beacon",
            key -> new WarBeaconItem(WAR_BEACON.get(), new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    private WarBlocks() {
    }

    /** Classload-forcing init (common init slot 3, after {@code EventPayloads.init()}); adds the creative item. */
    public static void init() {
        ModCreativeTab.add(WAR_BEACON_ITEM::get);
    }
}
