package za.co.neroland.neroevents.payload.registry;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/**
 * Items and block items that carry the one-sentence tooltip from the lang file. 26.x {@code Block} has no
 * {@code appendHoverText}, so a block's tooltip lives on its {@link BlockItem}.
 */
public final class TooltipItems {

    private TooltipItems() {
    }

    /** A plain item with {@code <tooltipKey>} in grey under its name. */
    public static class TooltipItem extends Item {

        private final String tooltipKey;

        public TooltipItem(Properties properties, String tooltipKey) {
            super(properties);
            this.tooltipKey = tooltipKey;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                Consumer<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, display, tooltip, flag);
            tooltip.accept(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
        }
    }

    /** A block item with {@code <tooltipKey>} in grey under its name. */
    public static class TooltipBlockItem extends BlockItem {

        private final String tooltipKey;

        public TooltipBlockItem(Block block, Properties properties, String tooltipKey) {
            super(block, properties);
            this.tooltipKey = tooltipKey;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                Consumer<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, display, tooltip, flag);
            tooltip.accept(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
        }
    }
}
