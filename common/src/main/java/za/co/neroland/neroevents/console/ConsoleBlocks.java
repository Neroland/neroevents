package za.co.neroland.neroevents.console;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerolandcore.registry.CoreCreativeTab;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.payload.registry.TooltipItems;

/**
 * The Event Console's registrations — block, block entity, menu and tooltip block item — through Neroland
 * Core's {@link RegistrationProvider}, in that order (Fabric registers when this class loads).
 *
 * <p><b>Fabric 26.1.2:</b> the two-argument {@code BlockEntityType} constructor is private there (public from
 * 26.2); {@code fabric/src/main/resources/neroevents.accesswidener} widens it.
 */
public final class ConsoleBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroEventsCommon.MOD_ID);
    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroEventsCommon.MOD_ID);
    public static final RegistrationProvider<MenuType<?>> MENUS =
            RegistrationProvider.get(Registries.MENU, NeroEventsCommon.MOD_ID);
    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroEventsCommon.MOD_ID);

    public static final RegistryEntry<Block> EVENT_CONSOLE = BLOCKS.register("event_console",
            key -> new EventConsoleBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.5F, 6.0F)
                    .lightLevel(state -> state.getValue(EventConsoleBlock.ACTIVE) ? 7 : 3)
                    .sound(SoundType.METAL)));

    public static final RegistryEntry<BlockEntityType<EventConsoleBlockEntity>> EVENT_CONSOLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("event_console",
                    key -> new BlockEntityType<>(EventConsoleBlockEntity::new, Set.of(EVENT_CONSOLE.get())));

    public static final RegistryEntry<MenuType<EventConsoleMenu>> EVENT_CONSOLE_MENU = MENUS.register("event_console",
            key -> new MenuType<>(EventConsoleMenu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryEntry<BlockItem> EVENT_CONSOLE_ITEM = ITEMS.register("event_console",
            key -> new TooltipItems.TooltipBlockItem(EVENT_CONSOLE.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix(),
                    "block." + NeroEventsCommon.MOD_ID + ".event_console.tooltip"));

    private ConsoleBlocks() {
    }

    /** Classload-forcing init (common init slot 3, after the war beacon); adds the console to Core's tab. */
    public static void init() {
        CoreCreativeTab.add(EVENT_CONSOLE_ITEM::get);
    }
}
