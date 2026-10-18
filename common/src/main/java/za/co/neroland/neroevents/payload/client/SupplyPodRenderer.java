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
import net.minecraft.util.Mth;

import za.co.neroland.neroevents.client.fx.RenderBudget;
import za.co.neroland.neroevents.payload.supply.SupplyPodEntity;

/**
 * The supply pod (geometry from {@code tools/entity-geometry.md} §1–2, §5). Descending: the canopy
 * sways {@code sin(age·0.08)·6°} with gore flutter, shroud lines shown. Landed: the canopy detaches and fades
 * over 30 ticks, the legs fold out over 8, the beacon lens pulses alpha 0.4 → 1.0 over 20 ticks in REWARD gold
 * and the ground-beacon sprite (a 4-frame strip, crossed quads at the lens) glows above it. The hatch swings
 * −95° over 10 ticks when someone has it open and shuts over 6. Culled beyond 128 blocks; the parachute beyond
 * 96; frustum always.
 */
public class SupplyPodRenderer extends EntityRenderer<SupplyPodEntity, SupplyPodRenderer.State> {

    static final double VIEW_DISTANCE = 128.0D;
    static final double PARACHUTE_DISTANCE = 96.0D;
    private static final Identifier[] TEXTURES = {
            PayloadRender.entityTexture("supply_pod_t1"),
            PayloadRender.entityTexture("supply_pod_t2"),
            PayloadRender.entityTexture("supply_pod_t3"),
    };
    private static final Identifier[] PARACHUTES = {
            PayloadRender.entityTexture("supply_parachute_t1"),
            PayloadRender.entityTexture("supply_parachute_t2"),
            PayloadRender.entityTexture("supply_parachute_t3"),
    };
    private static final Identifier GLOW = PayloadRender.entityTexture("supply_pod_glow");
    private static final Identifier BEACON = PayloadRender.entityTexture("pod_beacon_glow");

    /** What the pod needs per frame. */
    public static class State extends EntityRenderState {
        int tier = 1;
        float age;
        /** −1 while descending, else ticks since landing. */
        float sinceLanded = -1.0F;
        float hatch;
        boolean parachute;
    }

    /** The pod body: legs, hull, hatch, dome, lens and shroud lines. */
    static final class PodModel extends EntityModel<State> {
        private final ModelPart legNe;
        private final ModelPart legNw;
        private final ModelPart legSe;
        private final ModelPart legSw;
        private final ModelPart hatch;
        private final ModelPart[] lines;

        PodModel(ModelPart root) {
            super(root);
            this.legNe = root.getChild("leg_ne");
            this.legNw = root.getChild("leg_nw");
            this.legSe = root.getChild("leg_se");
            this.legSw = root.getChild("leg_sw");
            ModelPart hull = root.getChild("hull");
            this.hatch = hull.getChild("hatch");
            ModelPart dome = hull.getChild("dome");
            this.lines = new ModelPart[] {dome.getChild("line_0"), dome.getChild("line_1"), dome.getChild("line_2"),
                    dome.getChild("line_3")};
        }

        static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 42).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F);
            root.addOrReplaceChild("leg_ne", leg, PartPose.offset(5.0F, 20.0F, -5.0F));
            root.addOrReplaceChild("leg_nw", leg, PartPose.offset(-5.0F, 20.0F, -5.0F));
            root.addOrReplaceChild("leg_se", leg, PartPose.offset(5.0F, 20.0F, 5.0F));
            root.addOrReplaceChild("leg_sw", leg, PartPose.offset(-5.0F, 20.0F, 5.0F));
            PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-7.0F, -14.0F, -7.0F, 14.0F, 14.0F, 14.0F), PartPose.offset(0.0F, 20.0F, 0.0F));
            hull.addOrReplaceChild("hatch", CubeListBuilder.create().texOffs(40, 28)
                    .addBox(-4.0F, -8.0F, -1.0F, 8.0F, 8.0F, 1.0F), PartPose.offset(0.0F, 0.0F, -7.0F));
            PartDefinition dome = hull.addOrReplaceChild("dome", CubeListBuilder.create().texOffs(0, 28)
                    .addBox(-5.0F, -4.0F, -5.0F, 10.0F, 4.0F, 10.0F), PartPose.offset(0.0F, -14.0F, 0.0F));
            dome.addOrReplaceChild("beacon_lens", CubeListBuilder.create().texOffs(8, 42)
                    .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 2.0F, 4.0F), PartPose.offset(0.0F, -4.0F, 0.0F));
            CubeListBuilder line = CubeListBuilder.create().texOffs(24, 42)
                    .addBox(-0.5F, -14.0F, -0.5F, 1.0F, 14.0F, 1.0F);
            dome.addOrReplaceChild("line_0", line, PartPose.offset(4.0F, -4.0F, -4.0F));
            dome.addOrReplaceChild("line_1", line, PartPose.offset(-4.0F, -4.0F, -4.0F));
            dome.addOrReplaceChild("line_2", line, PartPose.offset(4.0F, -4.0F, 4.0F));
            dome.addOrReplaceChild("line_3", line, PartPose.offset(-4.0F, -4.0F, 4.0F));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            float fold = state.sinceLanded < 0.0F ? 0.0F : Math.min(1.0F, state.sinceLanded / 8.0F);
            float a = PayloadRender.rad(10.0F) * fold;
            legNe.zRot = -a;
            legNe.xRot = a;
            legNw.zRot = a;
            legNw.xRot = a;
            legSe.zRot = -a;
            legSe.xRot = -a;
            legSw.zRot = a;
            legSw.xRot = -a;
            hatch.xRot = -PayloadRender.rad(95.0F) * state.hatch;
            boolean descending = state.sinceLanded < 0.0F;
            for (ModelPart line : lines) {
                line.visible = descending;
            }
        }
    }

    /** The parachute: four gores and two apex vents, hung 18 texels above the dome. */
    static final class CanopyModel extends EntityModel<State> {
        private final ModelPart canopy;
        private final ModelPart[] gores;
        private final float[] goreBaseX;

        CanopyModel(ModelPart root) {
            super(root);
            this.canopy = root.getChild("canopy");
            this.gores = new ModelPart[] {canopy.getChild("gore_ne"), canopy.getChild("gore_nw"),
                    canopy.getChild("gore_se"), canopy.getChild("gore_sw")};
            this.goreBaseX = new float[] {PayloadRender.rad(22.5F), PayloadRender.rad(22.5F),
                    PayloadRender.rad(-22.5F), PayloadRender.rad(-22.5F)};
        }

        static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            // Dome pivot in pod space is y 6 (hull 20 − 14); the canopy hangs 18 above it.
            PartDefinition canopy = mesh.getRoot().addOrReplaceChild("canopy", CubeListBuilder.create(),
                    PartPose.offset(0.0F, -12.0F, 0.0F));
            float d = PayloadRender.rad(22.5F);
            canopy.addOrReplaceChild("gore_ne", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(0.0F, -1.0F, -16.0F, 16.0F, 1.0F, 16.0F), PartPose.rotation(d, 0.0F, -d));
            canopy.addOrReplaceChild("gore_nw", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-16.0F, -1.0F, -16.0F, 16.0F, 1.0F, 16.0F), PartPose.rotation(d, 0.0F, d));
            canopy.addOrReplaceChild("gore_se", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(0.0F, -1.0F, 0.0F, 16.0F, 1.0F, 16.0F), PartPose.rotation(-d, 0.0F, -d));
            canopy.addOrReplaceChild("gore_sw", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-16.0F, -1.0F, 0.0F, 16.0F, 1.0F, 16.0F), PartPose.rotation(-d, 0.0F, d));
            canopy.addOrReplaceChild("vent_a", CubeListBuilder.create().texOffs(0, 17)
                    .addBox(-3.0F, -2.0F, -3.0F, 6.0F, 1.0F, 6.0F), PartPose.ZERO);
            canopy.addOrReplaceChild("vent_b", CubeListBuilder.create().texOffs(0, 17)
                    .addBox(-3.0F, -3.0F, -3.0F, 6.0F, 1.0F, 6.0F), PartPose.rotation(0.0F, PayloadRender.rad(45.0F), 0.0F));
            return LayerDefinition.create(mesh, 64, 32);
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            float sway = PayloadRender.rad(Mth.sin(state.age * 0.08F) * 6.0F);
            canopy.xRot = sway;
            canopy.zRot = sway;
            for (int i = 0; i < gores.length; i++) {
                gores[i].xRot = goreBaseX[i] + PayloadRender.rad(Mth.sin(state.age * 0.35F + i) * 2.0F);
            }
        }
    }

    private final PodModel pod;
    private final CanopyModel canopy;

    public SupplyPodRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.pod = new PodModel(PodModel.createBodyLayer().bakeRoot());
        this.canopy = new CanopyModel(CanopyModel.createBodyLayer().bakeRoot());
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    //? if >=26.3 {
    /*@Override
    public boolean shouldRender(SupplyPodEntity entity, Frustum frustum, double camX, double camY, double camZ,
            float partialTick) {
        boolean show = entity.distanceToSqr(camX, camY, camZ) <= VIEW_DISTANCE * VIEW_DISTANCE
                && super.shouldRender(entity, frustum, camX, camY, camZ, partialTick);
        if (!show) {
            RenderBudget.culled();
        }
        return show;
    }
    *///?} else {
    @Override
    public boolean shouldRender(SupplyPodEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        boolean show = entity.distanceToSqr(camX, camY, camZ) <= VIEW_DISTANCE * VIEW_DISTANCE
                && super.shouldRender(entity, frustum, camX, camY, camZ);
        if (!show) {
            RenderBudget.culled();
        }
        return show;
    }
    //?}

    @Override
    public void extractRenderState(SupplyPodEntity entity, State state, float partialTick) {
        RenderBudget.frame(entity.level().getGameTime(), partialTick);
        RenderBudget.drawn(RenderBudget.Kind.POD);
        super.extractRenderState(entity, state, partialTick);
        state.tier = entity.tier();
        state.age = entity.tickCount + partialTick;
        state.sinceLanded = entity.ticksSinceLanded(partialTick);
        state.hatch = entity.hatchOpen(partialTick);
        state.parachute = state.distanceToCameraSq <= PARACHUTE_DISTANCE * PARACHUTE_DISTANCE
                && (state.sinceLanded < 0.0F || state.sinceLanded < 30.0F);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        long budgetStart = RenderBudget.start();
        int tier = Math.max(1, Math.min(3, state.tier)) - 1;
        boolean landed = state.sinceLanded >= 0.0F;
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        pod.setupAnim(state);
        PayloadRender.submitModel(collector, pod, state, poseStack, pod.renderType(TEXTURES[tier]), state.lightCoords, -1);
        float lens = landed ? 0.4F + 0.6F * (0.5F + 0.5F * Mth.sin(state.age * (float) (Math.PI * 2.0D / 20.0D))) : 1.0F;
        PayloadRender.submitModel(collector, pod, state, poseStack, RenderTypes.entityTranslucentEmissive(GLOW),
                PayloadRender.FULL_BRIGHT, PayloadRender.argb(PayloadRender.REWARD_RGB, lens));
        if (state.parachute) {
            float fade = landed ? Math.max(0.0F, 1.0F - state.sinceLanded / 30.0F) : 1.0F;
            poseStack.pushPose();
            if (landed) {
                poseStack.translate(0.0F, -state.sinceLanded * 0.05F, 0.0F);
            }
            canopy.setupAnim(state);
            PayloadRender.submitModel(collector, canopy, state, poseStack, RenderTypes.entityTranslucent(PARACHUTES[tier]),
                    state.lightCoords, PayloadRender.argb(0xFFFFFF, fade));
            poseStack.popPose();
        }
        poseStack.popPose();
        if (landed) {
            final int frame = (int) (state.age / 3.0F) & 3;
            final int color = PayloadRender.argb(PayloadRender.REWARD_RGB, lens);
            poseStack.pushPose();
            poseStack.translate(0.0F, 1.4F, 0.0F);
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(BEACON),
                    (pose, c) -> PayloadRender.crossedSprite(c, pose, 1.0F, frame, color, PayloadRender.FULL_BRIGHT));
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
        RenderBudget.stop(budgetStart);
    }
}
