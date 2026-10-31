package za.co.neroland.neroevents.client.compat;

// Minecraft 1.21.1 - 1.21.8 only (empty on every other line). 1.21.9 replaced immediate-mode block-entity and
// entity rendering with render states + a SubmitNodeCollector. These look-alikes (same simple names, swapped in
// by an import rewrite in stonecutter.gradle on those lines) let the 26.x renderers compile and run unchanged:
// each frame the old render(..) call builds the state, then runs submit(..) straight into the buffer source.

//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

public interface BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState>
        extends net.minecraft.client.renderer.blockentity.BlockEntityRenderer<T> {

    S createRenderState();

    default void extractRenderState(T blockEntity, S state, float partialTick, Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
    }

    void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState);

    //? if <1.21.6 {
    /^// 1.21.6 dropped the block entity argument from shouldRenderOffScreen; renderers override the no-arg form.
    default boolean shouldRenderOffScreen() {
        return false;
    }

    @Override
    default boolean shouldRenderOffScreen(T blockEntity) {
        return shouldRenderOffScreen();
    }
    ^///?}

    //? if >=1.21.5 {
    @Override
    default void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay, Vec3 cameraPos) {
        draw(this, blockEntity, partialTick, poseStack, buffers, light, cameraPos);
    }
    //?} else {
    /^@Override
    default void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay) {
        draw(this, blockEntity, partialTick, poseStack, buffers, light,
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }
    ^///?}

    private static <T extends BlockEntity, S extends BlockEntityRenderState> void draw(
            BlockEntityRenderer<T, S> renderer, T blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int light, Vec3 cameraPos) {
        S state = renderer.createRenderState();
        renderer.extractRenderState(blockEntity, state, partialTick, cameraPos, null);
        state.lightCoords = light;
        renderer.submit(state, poseStack, new SubmitNodeCollector(buffers), new CameraRenderState(cameraPos));
    }
}
*///?}
