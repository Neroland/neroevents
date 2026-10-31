package za.co.neroland.neroevents.client.compat;

// Minecraft 1.21.1 only (empty on every other line). 1.21.2 re-based entity models on render states and a root
// ModelPart; before it EntityModel took the entity itself. This look-alike (swapped in by an import rewrite in
// stonecutter.gradle) keeps the 26.x shape: a root part, setupAnim(state) starting from the rest pose, and the
// whole tree drawn by renderToBuffer.

//? if <1.21.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;

public abstract class EntityModel<S extends EntityRenderState> extends Model {

    protected final ModelPart root;

    protected EntityModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public final ModelPart root() {
        return this.root;
    }

    public void setupAnim(S state) {
        this.root.getAllParts().forEach(ModelPart::resetPose);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        this.root.render(poseStack, consumer, light, overlay, color);
    }
}
*///?}
