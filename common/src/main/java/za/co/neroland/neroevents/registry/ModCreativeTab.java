package za.co.neroland.neroevents.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.console.ConsoleBlocks;

/**
 * NeroEvents' own creative tab, registered cross-loader through Neroland Core's {@link RegistrationProvider}
 * over the vanilla {@code CREATIVE_MODE_TAB} registry. Every Nero mod owns its tab; nothing is added to
 * Core's "Neroland" tab, which lists Core's own items only.
 *
 * <p>The registration classes contribute through {@link #add(Supplier)} during common init (payload items,
 * the war beacon, then the Event Console); suppliers resolve lazily when the tab is built. The Supply Beacon
 * stays out of the tab on purpose — it is a quest reward.
 */
public final class ModCreativeTab {

    public static final RegistrationProvider<CreativeModeTab> TABS =
            RegistrationProvider.get(Registries.CREATIVE_MODE_TAB, NeroEventsCommon.MOD_ID);

    /** Item suppliers in display order, drained into the tab when it is built. */
    private static final List<Supplier<? extends ItemLike>> CONTENTS = new ArrayList<>();

    // NOTE: vanilla CreativeModeTab.builder takes (Row, column); the no-arg overload and
    // withTabsBefore/After are NeoForge-only extensions, so they are avoided here (common = raw vanilla).
    public static final RegistryEntry<CreativeModeTab> NEROEVENTS = TABS.register("neroevents",
            key -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.neroevents"))
                    .icon(() -> new ItemStack(ConsoleBlocks.EVENT_CONSOLE_ITEM.get()))
                    .displayItems((params, output) -> CONTENTS.forEach(s -> output.accept(s.get())))
                    .build());

    private ModCreativeTab() {
    }

    /** Append an item to the NeroEvents tab. Call during common init, before tabs are built. */
    public static void add(Supplier<? extends ItemLike> item) {
        CONTENTS.add(item);
    }

    /** Force class-load so the static tab registration runs (common init slot 3, after the Event Console). */
    public static void init() {
    }
}
