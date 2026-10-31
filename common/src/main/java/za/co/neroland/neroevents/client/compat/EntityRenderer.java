package za.co.neroland.neroevents.client.compat;

// Minecraft 1.21.1 - 1.21.8 only (empty on every other line). Before 1.21.9 an entity renderer drew immediately
// in render(..); 1.21.9 replaced that with submit(.., collector, camera). This base (same simple name, swapped in
// by an import rewrite in stonecutter.gradle on those lines) declares the 26.x submit(..) and routes the old
// render(..) call into it, so the renderers compile unchanged. super.submit(..) draws what vanilla's render(..)
// drew (the name tag). On 1.21.1, which has no render states, it also builds the state from the entity.

//? if <1.21.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public abstract class EntityRenderer<T extends Entity, S extends EntityRenderState>
        extends net.minecraft.client.renderer.entity.EntityRenderer<T> {

    private T entity;
    private float yaw;
    private float partialTick;

    protected EntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public abstract S createRenderState();

    public void extractRenderState(T entity, S state, float partialTick) {
        state.x = net.minecraft.util.Mth.lerp(partialTick, entity.xOld, entity.getX());
        state.y = net.minecraft.util.Mth.lerp(partialTick, entity.yOld, entity.getY());
        state.z = net.minecraft.util.Mth.lerp(partialTick, entity.zOld, entity.getZ());
        state.ageInTicks = entity.tickCount + partialTick;
        state.distanceToCameraSq = this.entityRenderDispatcher.distanceToSqr(entity);
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int light) {
        S state = createRenderState();
        extractRenderState(entity, state, partialTick);
        state.lightCoords = light;
        this.entity = entity;
        this.yaw = yaw;
        this.partialTick = partialTick;
        try {
            submit(state, poseStack, new SubmitNodeCollector(buffers), new CameraRenderState());
        } finally {
            this.entity = null;
        }
    }

    public void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (this.entity != null) {
            super.render(this.entity, this.yaw, this.partialTick, poseStack, collector.buffers(), state.lightCoords);
        }
    }

    // The renderers draw their own textures through explicit render types; vanilla only asks for this one.
    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return MissingTextureAtlasSprite.getLocation();
    }
}
*///?} else if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;

public abstract class EntityRenderer<T extends Entity, S extends EntityRenderState>
        extends net.minecraft.client.renderer.entity.EntityRenderer<T, S> {

    protected EntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(S state, PoseStack poseStack, MultiBufferSource buffers, int light) {
        state.lightCoords = light;
        submit(state, poseStack, new SubmitNodeCollector(buffers), new CameraRenderState());
    }

    public void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        super.render(state, poseStack, collector.buffers(), state.lightCoords);
    }
}
*///?}
