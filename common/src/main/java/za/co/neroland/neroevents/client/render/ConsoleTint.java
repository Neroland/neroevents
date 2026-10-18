package za.co.neroland.neroevents.client.render;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.neroevents.client.EventColours;
import za.co.neroland.neroevents.console.EventConsoleBlockEntity;

/**
 * Tint layer 0 of the Event Console's active screen ({@code tintindex 0} in {@code event_console_active}): the
 * severity colour of the most urgent event, read from the block entity; {@code PLASMA} when it has none. The
 * block entity re-meshes its block when the severity changes. CLIENT-ONLY.
 */
public final class ConsoleTint implements BlockTintSource {

    public static final ConsoleTint INSTANCE = new ConsoleTint();

    private ConsoleTint() {
    }

    @Override
    public int color(BlockState state) {
        return EventColours.opaque(EventColours.PLASMA);
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (level != null && pos != null && level.getBlockEntity(pos) instanceof EventConsoleBlockEntity console
                && console.severity() != EventConsoleBlockEntity.NONE) {
            return EventColours.opaque(EventColours.severity(EventColours.severityOf(console.severity())));
        }
        return color(state);
    }
}
