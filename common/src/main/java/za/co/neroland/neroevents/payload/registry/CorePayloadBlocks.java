package za.co.neroland.neroevents.payload.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.payload.debris.SalvageCrateBlock;
import za.co.neroland.neroevents.payload.debris.WreckPaletteBlocks;
import za.co.neroland.neroevents.payload.meteor.CraterPocketBlock;

/**
 * The Core-only payloads' blocks (crater palette, wreck palette, the crater pocket and the salvage crate),
 * through Neroland Core's {@link RegistrationProvider}. Fabric registers when this class loads, which
 * {@link #init()} forces from common init slot 3; NeoForge and Forge attach the deferred register to their
 * mod bus. Registration order is blocks → block entities → entities → items.
 */
public final class CorePayloadBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroEventsCommon.MOD_ID);

    /** Cooled meteor rock: the crater rim. */
    public static final RegistryEntry<Block> METEOR_SLAG = BLOCKS.register("meteor_slag",
            key -> new Block(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.BASALT)));

    /** The glassy crater floor. */
    public static final RegistryEntry<Block> COOLED_CRUST = BLOCKS.register("cooled_crust",
            key -> new Block(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_BROWN)
                    .strength(1.2F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.TUFF)));

    /** Meteor rock with a Core material caught inside (drops through its block loot table). */
    public static final RegistryEntry<Block> EMBEDDED_METEOR_ORE = BLOCKS.register("embedded_meteor_ore",
            key -> new Block(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_BLACK)
                    .strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.BASALT)));

    /** The loot heart of a crater: breaking it rolls the pocket table and credits the breaker. */
    public static final RegistryEntry<Block> CRATER_POCKET = BLOCKS.register("crater_pocket",
            key -> new CraterPocketBlock(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_ORANGE)
                    .strength(1.0F, 6.0F).lightLevel(state -> 7).noLootTable().sound(SoundType.BASALT)));

    /** Starsteel plating torn from a wreck. */
    public static final RegistryEntry<Block> HULL_PLATING = BLOCKS.register("hull_plating",
            key -> new Block(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));

    /** Burnt plating; crumbles to rubble in time. */
    public static final RegistryEntry<Block> SCORCHED_PANEL = BLOCKS.register("scorched_panel",
            key -> new Block(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_GRAY)
                    .strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));

    /** A twisted support beam (facing, non-full shape). */
    public static final RegistryEntry<Block> BENT_STRUT = BLOCKS.register("bent_strut",
            key -> new WreckPaletteBlocks.BentStrutBlock(BlockBehaviour.Properties.of().setId(key)
                    .mapColor(MapColor.METAL).strength(1.5F, 6.0F).noOcclusion().sound(SoundType.METAL)));

    /** A crashed cargo crate: rolls its loot on the first open; refuses to break while it holds items. */
    public static final RegistryEntry<Block> SALVAGE_CRATE = BLOCKS.register("salvage_crate",
            key -> new SalvageCrateBlock(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.METAL)
                    .strength(2.5F, 6.0F).noOcclusion().noLootTable().sound(SoundType.METAL)));

    private CorePayloadBlocks() {
    }

    /** Classload-forcing no-op (common init slot 3, first of the four payload registries). */
    public static void init() {
    }
}
