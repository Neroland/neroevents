package za.co.neroland.neroevents.payload.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.client.fx.RenderBudget;
import za.co.neroland.neroevents.payload.debris.DebrisEntity;

/**
 * Falling debris (geometry from {@code tools/entity-geometry.md} §4): a bent plate, a torn strut and
 * a pipe stub tumbling 4°/t on yaw and pitch. The conduit stub's end rings glow full-bright and sputter
 * (alpha 1.0 with 30 % chance per tick, else 0.2). Culled beyond 96 blocks; frustum always.
 */
public class DebrisRenderer extends EntityRenderer<DebrisEntity, DebrisRenderer.State> {

    static final double VIEW_DISTANCE = 96.0D;
    private static final Identifier TEXTURE = PayloadRender.entityTexture("debris");
    private static final Identifier GLOW = PayloadRender.entityTexture("debris_glow");

    /** What the debris needs per frame. */
    public static class State extends EntityRenderState {
        float age;
        float seed;
        float glowAlpha;
    }

    /** The five-part fragment. */
    static final class Model extends EntityModel<State> {
        private final ModelPart fragment;

        Model(ModelPart root) {
            super(root);
            this.fragment = root.getChild("root");
        }

        static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(),
                    PartPose.offset(0.0F, 5.0F, 0.0F));
            PartDefinition plateA = root.addOrReplaceChild("plate_a", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-8.0F, -1.0F, -5.0F, 16.0F, 2.0F, 10.0F), PartPose.ZERO);
            plateA.addOrReplaceChild("plate_b", CubeListBuilder.create().texOffs(0, 12)
                    .addBox(-2.0F, -1.0F, -5.0F, 12.0F, 2.0F, 10.0F),
                    PartPose.offsetAndRotation(6.0F, 0.0F, 0.0F, PayloadRender.rad(15.0F), 0.0F, 0.0F));
            root.addOrReplaceChild("strut", CubeListBuilder.create().texOffs(0, 24)
                    .addBox(-1.0F, -12.0F, -1.0F, 2.0F, 14.0F, 2.0F),
                    PartPose.offsetAndRotation(-4.0F, 0.0F, 3.0F, 0.0F, 0.0F, PayloadRender.rad(35.0F)));
            PartDefinition pipe = root.addOrReplaceChild("pipe", CubeListBuilder.create().texOffs(8, 24)
                    .addBox(-1.5F, -1.5F, -5.0F, 3.0F, 3.0F, 10.0F),
                    PartPose.offsetAndRotation(2.0F, -2.0F, -4.0F, 0.0F, PayloadRender.rad(20.0F), 0.0F));
            pipe.addOrReplaceChild("conduit_stub", CubeListBuilder.create().texOffs(34, 24)
                    .addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F), PartPose.offset(0.0F, 0.0F, -5.0F));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            fragment.yRot = PayloadRender.rad(state.age * 4.0F + state.seed);
            fragment.xRot = PayloadRender.rad(state.age * 4.0F + state.seed * 0.5F);
        }
    }

    private final Model model;

    public DebrisRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new Model(Model.createBodyLayer().bakeRoot());
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    //? if >=26.3 {
    /*@Override
    public boolean shouldRender(DebrisEntity debris, Frustum frustum, double camX, double camY, double camZ,
            float partialTick) {
        boolean show = debris.distanceToSqr(camX, camY, camZ) <= VIEW_DISTANCE * VIEW_DISTANCE
                && super.shouldRender(debris, frustum, camX, camY, camZ, partialTick);
        if (!show) {
            RenderBudget.culled();
        }
        return show;
    }
    *///?} else {
    @Override
    public boolean shouldRender(DebrisEntity debris, Frustum frustum, double camX, double camY, double camZ) {
        boolean show = debris.distanceToSqr(camX, camY, camZ) <= VIEW_DISTANCE * VIEW_DISTANCE
                && super.shouldRender(debris, frustum, camX, camY, camZ);
        if (!show) {
            RenderBudget.culled();
        }
        return show;
    }
    //?}

    @Override
    public void extractRenderState(DebrisEntity debris, State state, float partialTick) {
        RenderBudget.frame(debris.level().getGameTime(), partialTick);
        RenderBudget.drawn(RenderBudget.Kind.DEBRIS);
        super.extractRenderState(debris, state, partialTick);
        state.age = debris.tickCount + partialTick;
        state.seed = Math.floorMod(debris.getUUID().hashCode(), 360);
        // Deterministic per tick so every frame of one tick agrees: a 30 % chance of a full sputter.
        int hash = (debris.tickCount * 0x9E3779B1) ^ debris.getId() * 0x85EBCA6B;
        state.glowAlpha = Math.floorMod(hash, 100) < 30 ? 1.0F : 0.2F;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        long budgetStart = RenderBudget.start();
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -0.6125F, 0.0F);
        model.setupAnim(state);
        PayloadRender.submitModel(collector, model, state, poseStack, model.renderType(TEXTURE), state.lightCoords, -1);
        PayloadRender.submitModel(collector, model, state, poseStack, RenderTypes.entityTranslucentEmissive(GLOW),
                PayloadRender.FULL_BRIGHT, PayloadRender.argb(0xFFFFFF, state.glowAlpha));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
        RenderBudget.stop(budgetStart);
    }
}
