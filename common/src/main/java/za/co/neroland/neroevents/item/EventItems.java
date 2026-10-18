package za.co.neroland.neroevents.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * NeroEvents' item registrations through Neroland Core's {@link RegistrationProvider} (common init slot 3;
 * NeoForge and Forge attach the deferred register to their mod bus). No creative tab entry: the Supply
 * Beacon is a quest reward.
 */
public final class EventItems {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroEventsCommon.MOD_ID);

    /** {@code neroevents:supply_beacon}: one use, calls a supply drop where the holder stands. */
    public static final RegistryEntry<Item> SUPPLY_BEACON = ITEMS.register("supply_beacon",
            key -> new SupplyBeaconItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));

    private EventItems() {
    }

    /** Classload-forcing no-op (common init slot 3). */
    public static void init() {
    }
}
