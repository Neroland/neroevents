package za.co.neroland.neroevents.payload.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.registry.ModCreativeTab;

/**
 * The Core-only payloads' items: the three salvage items and a tooltip-carrying {@link BlockItem} for every
 * payload block. Everything here joins the NeroEvents creative tab (players find these blocks in the world
 * and want to search for them); the Supply Beacon stays out of it (a quest reward).
 */
public final class CorePayloadItems {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroEventsCommon.MOD_ID);

    public static final RegistryEntry<Item> SALVAGE_PLATING = item("salvage_plating");
    public static final RegistryEntry<Item> SALVAGE_CONDUIT = item("salvage_conduit");
    public static final RegistryEntry<Item> SALVAGE_CORE_FRAGMENT = item("salvage_core_fragment");

    public static final RegistryEntry<BlockItem> METEOR_SLAG = blockItem("meteor_slag", CorePayloadBlocks.METEOR_SLAG);
    public static final RegistryEntry<BlockItem> COOLED_CRUST = blockItem("cooled_crust", CorePayloadBlocks.COOLED_CRUST);
    public static final RegistryEntry<BlockItem> EMBEDDED_METEOR_ORE =
            blockItem("embedded_meteor_ore", CorePayloadBlocks.EMBEDDED_METEOR_ORE);
    public static final RegistryEntry<BlockItem> CRATER_POCKET = blockItem("crater_pocket", CorePayloadBlocks.CRATER_POCKET);
    public static final RegistryEntry<BlockItem> HULL_PLATING = blockItem("hull_plating", CorePayloadBlocks.HULL_PLATING);
    public static final RegistryEntry<BlockItem> SCORCHED_PANEL =
            blockItem("scorched_panel", CorePayloadBlocks.SCORCHED_PANEL);
    public static final RegistryEntry<BlockItem> BENT_STRUT = blockItem("bent_strut", CorePayloadBlocks.BENT_STRUT);
    public static final RegistryEntry<BlockItem> SALVAGE_CRATE = blockItem("salvage_crate", CorePayloadBlocks.SALVAGE_CRATE);

    private static RegistryEntry<Item> item(String name) {
        return ITEMS.register(name, key -> new TooltipItems.TooltipItem(new Item.Properties().setId(key),
                "item." + NeroEventsCommon.MOD_ID + "." + name + ".tooltip"));
    }

    private static RegistryEntry<BlockItem> blockItem(String name, RegistryEntry<? extends Block> block) {
        return ITEMS.register(name, key -> new TooltipItems.TooltipBlockItem(block.get(),
                new Item.Properties().setId(key).useBlockDescriptionPrefix(),
                "block." + NeroEventsCommon.MOD_ID + "." + name + ".tooltip"));
    }

    private CorePayloadItems() {
    }

    /** Classload-forcing no-op (last of the four payload registries), then the creative-tab entries. */
    public static void init() {
        ModCreativeTab.add(METEOR_SLAG::get);
        ModCreativeTab.add(COOLED_CRUST::get);
        ModCreativeTab.add(EMBEDDED_METEOR_ORE::get);
        ModCreativeTab.add(CRATER_POCKET::get);
        ModCreativeTab.add(HULL_PLATING::get);
        ModCreativeTab.add(SCORCHED_PANEL::get);
        ModCreativeTab.add(BENT_STRUT::get);
        ModCreativeTab.add(SALVAGE_CRATE::get);
        ModCreativeTab.add(SALVAGE_PLATING::get);
        ModCreativeTab.add(SALVAGE_CONDUIT::get);
        ModCreativeTab.add(SALVAGE_CORE_FRAGMENT::get);
    }
}
