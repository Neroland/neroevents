package za.co.neroland.neroevents.client.compat;

// Minecraft 1.21.1 - 1.21.8 only (empty on every other line). 1.21.9 replaced immediate-mode block-entity and
// entity rendering with render states + a SubmitNodeCollector. These look-alikes (same simple names, swapped in
// by an import rewrite in stonecutter.gradle on those lines) let the 26.x renderers compile and run unchanged:
// each frame the old render(..) call builds the state, then runs submit(..) straight into the buffer source.

//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import org.jetbrains.annotations.Nullable;

public final class SubmitNodeCollector {

    private final MultiBufferSource buffers;

    public SubmitNodeCollector(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    public MultiBufferSource buffers() {
        return this.buffers;
    }

    // Draw order only matters to the deferred 1.21.9+ pipeline; here everything is drawn immediately.
    public SubmitNodeCollector order(int order) {
        return this;
    }

    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
        renderer.render(poseStack.last(), this.buffers.getBuffer(renderType));
    }

    public <S> void submitModel(Model model, S state, PoseStack poseStack, RenderType renderType, int light,
            int overlay, int color, @Nullable Object sprite, int outlineColor, @Nullable Object crumbling) {
        //? if >=1.21.2 {
        if (model instanceof net.minecraft.client.model.EntityModel<?> entityModel
                && state instanceof net.minecraft.client.renderer.entity.state.EntityRenderState renderState) {
            setup(entityModel, renderState);
        }
        //?} else {
        /^if (model instanceof EntityModel<?> entityModel && state instanceof EntityRenderState renderState) {
            setup(entityModel, renderState);
        }
        ^///?}
        model.renderToBuffer(poseStack, this.buffers.getBuffer(renderType), light, overlay, color);
    }

    //? if >=1.21.2 {
    @SuppressWarnings("unchecked")
    private static void setup(net.minecraft.client.model.EntityModel<?> model,
            net.minecraft.client.renderer.entity.state.EntityRenderState state) {
        ((net.minecraft.client.model.EntityModel<net.minecraft.client.renderer.entity.state.EntityRenderState>) model)
                .setupAnim(state);
    }
    //?} else {
    /^@SuppressWarnings("unchecked")
    private static void setup(EntityModel<?> model, EntityRenderState state) {
        ((EntityModel<EntityRenderState>) model).setupAnim(state);
    }
    ^///?}

    @FunctionalInterface
    public interface CustomGeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer consumer);
    }
}
*///?}
