package za.co.neroland.neroevents.client.render;

//? if >=26.1 {
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
//?} else {
/*import net.minecraft.client.color.block.BlockColor;
import net.minecraft.world.level.BlockAndTintGetter;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.neroevents.client.EventColours;
import za.co.neroland.neroevents.console.EventConsoleBlockEntity;

/**
 * Tint layer 0 of the Event Console's active screen ({@code tintindex 0} in {@code event_console_active}): the
 * severity colour of the most urgent event, read from the block entity; {@code PLASMA} when it has none. The
 * block entity re-meshes its block when the severity changes. CLIENT-ONLY.
 */
//? if >=26.1 {
public final class ConsoleTint implements BlockTintSource {
//?} else {
/*public final class ConsoleTint implements BlockColor {
*///?}

    public static final ConsoleTint INSTANCE = new ConsoleTint();

    private ConsoleTint() {
    }

    //? if >=26.1 {
    @Override
    //?}
    public int color(BlockState state) {
        return EventColours.opaque(EventColours.PLASMA);
    }

    //? if <26.1 {
    /*// Before 26.1 a block's tint came from one BlockColor per block, asked for every tint index (only 0 is used).
    @Override
    public int getColor(BlockState state, @org.jetbrains.annotations.Nullable BlockAndTintGetter level,
            @org.jetbrains.annotations.Nullable BlockPos pos, int tintIndex) {
        return colorInWorld(state, level, pos);
    }
    *///?}

    //? if >=26.1 {
    @Override
    //?}
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (level != null && pos != null && level.getBlockEntity(pos) instanceof EventConsoleBlockEntity console
                && console.severity() != EventConsoleBlockEntity.NONE) {
            return EventColours.opaque(EventColours.severity(EventColours.severityOf(console.severity())));
        }
        return color(state);
    }
}
