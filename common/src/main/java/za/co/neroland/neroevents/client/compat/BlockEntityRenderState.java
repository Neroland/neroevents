package za.co.neroland.neroevents.client.compat;

// Minecraft 1.21.1 - 1.21.8 only (empty on every other line). 1.21.9 replaced immediate-mode block-entity and
// entity rendering with render states + a SubmitNodeCollector. These look-alikes (same simple names, swapped in
// by an import rewrite in stonecutter.gradle on those lines) let the 26.x renderers compile and run unchanged:
// each frame the old render(..) call builds the state, then runs submit(..) straight into the buffer source.

//? if <1.21.9 {
/*import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

public class BlockEntityRenderState {

    public BlockPos blockPos = BlockPos.ZERO;
    public BlockState blockState = Blocks.AIR.defaultBlockState();
    @Nullable
    public BlockEntityType<?> blockEntityType;
    public int lightCoords;
    @Nullable
    public ModelFeatureRenderer.CrumblingOverlay breakProgress;

    public static void extractBase(BlockEntity blockEntity, BlockEntityRenderState state,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        state.blockPos = blockEntity.getBlockPos();
        state.blockState = blockEntity.getBlockState();
        state.blockEntityType = blockEntity.getType();
        state.breakProgress = breakProgress;
    }
}
*///?}
