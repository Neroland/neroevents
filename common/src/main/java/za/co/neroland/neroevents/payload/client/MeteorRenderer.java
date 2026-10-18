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
import za.co.neroland.neroevents.payload.meteor.MeteorEntity;

/**
 * The meteor (geometry from {@code tools/entity-geometry.md} §3): an irregular lump of three rotated
 * boxes tumbling {@code yaw += 7°/t, pitch += 3°/t} (seeded per meteor so a shower is not synchronous). Base pass
 * lit, fissure glow full-bright and untinted, then a translucent heat shell — the core box scaled 1.4 → 1.2 as
 * the fall slows, its strip UV-scrolled {@code (age·0.05) mod 1}, depth-tested. Culled beyond 160 blocks; the
 * heat pass beyond 96; frustum always.
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity, MeteorRenderer.State> {

    static final double VIEW_DISTANCE = 160.0D;
    static final double HEAT_DISTANCE = 96.0D;
    private static final Identifier TEXTURE = PayloadRender.entityTexture("meteor");
    private static final Identifier GLOW = PayloadRender.entityTexture("meteor_glow");
    private static final Identifier HEAT = PayloadRender.entityTexture("meteor_heat");

    /** What the meteor needs per frame. */
    public static class State extends EntityRenderState {
        float age;
        float seed;
        float speed;
        boolean heat;
    }

    /** The three-box lump. */
    static final class Model extends EntityModel<State> {
        private final ModelPart lump;

        Model(ModelPart root) {
            super(root);
            this.lump = root.getChild("root");
        }

        static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(),
                    PartPose.offset(0.0F, 12.0F, 0.0F));
            root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-7.0F, -6.0F, -8.0F, 14.0F, 12.0F, 16.0F), PartPose.ZERO);
            root.addOrReplaceChild("chunk_a", CubeListBuilder.create().texOffs(0, 28)
                    .addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                    PartPose.offsetAndRotation(5.0F, -3.0F, 4.0F, PayloadRender.rad(22.5F), 0.0F,
                            PayloadRender.rad(22.5F)));
            root.addOrReplaceChild("chunk_b", CubeListBuilder.create().texOffs(32, 28)
                    .addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                    PartPose.offsetAndRotation(-5.0F, 3.0F, -4.0F, 0.0F, PayloadRender.rad(45.0F),
                            PayloadRender.rad(22.5F)));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            lump.yRot = PayloadRender.rad(state.age * 7.0F + state.seed);
            lump.xRot = PayloadRender.rad(state.age * 3.0F + state.seed * 0.5F);
        }
    }

    private final Model model;

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new Model(Model.createBodyLayer().bakeRoot());
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    //? if >=26.3 {
    /*@Override
    public boolean shouldRender(MeteorEntity meteor, Frustum frustum, double camX, double camY, double camZ,
            float partialTick) {
        boolean show = meteor.distanceToSqr(camX, camY, camZ) <= VIEW_DISTANCE * VIEW_DISTANCE
                && super.shouldRender(meteor, frustum, camX, camY, camZ, partialTick);
        if (!show) {
            RenderBudget.culled();
        }
        return show;
    }
    *///?} else {
    @Override
    public boolean shouldRender(MeteorEntity meteor, Frustum frustum, double camX, double camY, double camZ) {
        boolean show = meteor.distanceToSqr(camX, camY, camZ) <= VIEW_DISTANCE * VIEW_DISTANCE
                && super.shouldRender(meteor, frustum, camX, camY, camZ);
        if (!show) {
            RenderBudget.culled();
        }
        return show;
    }
    //?}

    @Override
    public void extractRenderState(MeteorEntity meteor, State state, float partialTick) {
        RenderBudget.frame(meteor.level().getGameTime(), partialTick);
        RenderBudget.drawn(RenderBudget.Kind.METEOR);
        super.extractRenderState(meteor, state, partialTick);
        state.age = meteor.tickCount + partialTick;
        state.seed = (Math.floorMod(meteor.getUUID().hashCode() + meteor.index() * 977, 360));
        state.speed = (float) meteor.flightVelocity().length();
        state.heat = state.distanceToCameraSq <= HEAT_DISTANCE * HEAT_DISTANCE;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        long budgetStart = RenderBudget.start();
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.35F, 0.0F);
        model.setupAnim(state);
        PayloadRender.submitModel(collector, model, state, poseStack, model.renderType(TEXTURE), state.lightCoords, -1);
        PayloadRender.submitModel(collector, model, state, poseStack, RenderTypes.eyes(GLOW), PayloadRender.FULL_BRIGHT,
                -1);
        poseStack.popPose();
        if (state.heat) {
            float scale = 1.2F + 0.2F * Math.min(1.0F, state.speed / 1.6F);
            // (age·0.05) mod 1, mapped onto the strip's first three quarters so the window never leaves the texture.
            float scroll = ((state.age * 0.05F) % 1.0F) * 0.75F;
            float yaw = state.age * 7.0F + state.seed;
            float pitch = state.age * 3.0F + state.seed * 0.5F;
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.6F, 0.0F);
            PayloadRender.rotateY(poseStack, -yaw);
            PayloadRender.rotateX(poseStack, -pitch);
            final float hx = 7.0F / 16.0F * scale;
            final float hy = 6.0F / 16.0F * scale;
            final float hz = 8.0F / 16.0F * scale;
            final int color = PayloadRender.argb(0xFFFFFF, 0.6F);
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(HEAT),
                    (pose, c) -> PayloadRender.scrolledBox(c, pose, hx, hy, hz, scroll, color, PayloadRender.FULL_BRIGHT));
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
        RenderBudget.stop(budgetStart);
    }
}
