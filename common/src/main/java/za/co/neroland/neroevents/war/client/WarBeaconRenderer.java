package za.co.neroland.neroevents.war.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.client.fx.RenderBudget;
import za.co.neroland.neroevents.war.WarBeaconBlockEntity;
import za.co.neroland.neroevents.war.WarColours;

/**
 * Draws everything of a war beacon above its plinth model, from the synced block entity only (geometry per
 * {@code tools/entity-geometry.md} § "war_beacon BER"): the column and head (world light), the faction band and
 * the eight-plate emitter ring (full-bright, tinted by owner, blinking 10 t / 10 t between the two faction colours
 * while contested; ring yaw 2°/t idle, 6°/t contested) and the translucent ground capture ring of radius
 * {@code captureRadius + 0.5}, with the capturing side's colour sweeping clockwise from north by progress.
 *
 * <p>Culling: {@link #shouldRender} is a distance check against the beacon (never a blanket {@code true}); the
 * pylon draws within 96 blocks, the ground ring within 64. Numbers only, never names: nothing here draws a name.
 * Textures are bound directly ({@code textures/block/war_beacon_*.png}); the animated ring strip (16×96, 6 frames,
 * frametime 2) is stepped here.
 */
public class WarBeaconRenderer implements BlockEntityRenderer<WarBeaconBlockEntity, WarBeaconRenderer.State> {

    private static final Identifier COLUMN = texture("war_beacon_column");
    private static final Identifier HEAD = texture("war_beacon_head");
    private static final Identifier BASE = texture("war_beacon_base");
    private static final Identifier BAND = texture("war_beacon_band");
    private static final Identifier RING = texture("war_beacon_ring");

    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final double PYLON_DISTANCE = 96.0D;
    private static final double GROUND_RING_DISTANCE = 64.0D;
    private static final int RING_FRAMES = 6;
    private static final int RING_FRAMETIME = 2;
    private static final int GROUND_SEGMENTS = 24;

    /** Render state: everything extracted from the block entity and clocks; nothing read at submit time. */
    public static class State extends BlockEntityRenderState {
        boolean visible;
        boolean groundRing;
        int bandRgb = WarColours.UNOWNED;
        float ringYaw;
        int ringFrame;
        int light = FULL_BRIGHT;
        int radius = WarBeaconBlockEntity.DEFAULT_CAPTURE_RADIUS;
        float sweep;
        boolean contested;
        int baseArgb;
        int sweepArgb;
        int contestedArgb;
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "textures/block/" + name + ".png");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    /** NeoForge-only frustum hook (inert elsewhere): covers the ground ring and the two-block pylon. */
    public AABB getRenderBoundingBox(WarBeaconBlockEntity beacon) {
        int r = beacon.captureRadius() + 1;
        return new AABB(beacon.getBlockPos()).inflate(r, 0, r).expandTowards(0, 2, 0);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true; // the ground ring reaches beyond the beacon's own section; distance-culled below
    }

    @Override
    public boolean shouldRender(WarBeaconBlockEntity beacon, Vec3 cameraPos) {
        boolean show = Vec3.atCenterOf(beacon.getBlockPos()).closerThan(cameraPos, PYLON_DISTANCE);
        if (!show) {
            RenderBudget.culled();
        }
        return show;
    }

    @Override
    public void extractRenderState(WarBeaconBlockEntity beacon, State state, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(beacon, state, partialTick, cameraPos, breakProgress);
        Level level = beacon.getLevel();
        BlockPos pos = beacon.getBlockPos();
        Vec3 centre = Vec3.atCenterOf(pos);
        if (level == null) {
            state.visible = false;
            return;
        }
        state.visible = centre.closerThan(cameraPos, PYLON_DISTANCE);
        if (!state.visible) {
            return;
        }
        RenderBudget.frame(level.getGameTime(), partialTick);
        RenderBudget.drawn(RenderBudget.Kind.WAR_BEACON);
        state.groundRing = centre.closerThan(cameraPos, GROUND_RING_DISTANCE);
        state.light = state.lightCoords == 0 ? FULL_BRIGHT : state.lightCoords;
        long gameTime = level.getGameTime();
        boolean blinkA = (gameTime / 10L) % 2L == 0L;
        int a = beacon.colourA();
        int b = beacon.colourB();
        byte owner = beacon.owner();
        state.contested = beacon.contested();
        int ownerRgb = owner == WarBeaconBlockEntity.SIDE_A ? a : owner == WarBeaconBlockEntity.SIDE_B ? b
                : WarColours.UNOWNED;
        state.bandRgb = state.contested ? (blinkA ? a : b) : ownerRgb;
        state.ringYaw = beacon.advanceRingYaw(gameTime + partialTick);
        state.ringFrame = (int) ((gameTime / RING_FRAMETIME) % RING_FRAMES);
        state.radius = beacon.captureRadius();
        state.sweep = Math.min(1.0F, beacon.progressTicks() / (float) Math.max(1, beacon.captureTicks()));
        state.baseArgb = owner == WarBeaconBlockEntity.NONE ? argb(WarColours.UNOWNED, 0.25F) : argb(ownerRgb, 0.35F);
        int contesting;
        if (owner == WarBeaconBlockEntity.SIDE_A) {
            contesting = b;
        } else if (owner == WarBeaconBlockEntity.SIDE_B) {
            contesting = a;
        } else {
            byte side = beacon.progressSide();
            contesting = side == WarBeaconBlockEntity.SIDE_A ? a : side == WarBeaconBlockEntity.SIDE_B ? b
                    : WarColours.UNOWNED;
        }
        state.sweepArgb = argb(contesting, 0.45F);
        state.contestedArgb = argb(blinkA ? a : b, 0.35F);
    }

    private static int argb(int rgb, float alpha) {
        return ((int) (alpha * 255.0F + 0.5F) << 24) | (rgb & 0xFFFFFF);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.visible) {
            return;
        }
        int light = state.light;
        // Column (sides + up) and head (sides + up; down from the base sheet): world light.
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(COLUMN), (pose, c) -> {
            sides(c, pose, light, 0xFFFFFFFF, 5 / 16F, 14 / 16F, 5 / 16F, 11 / 16F, 28 / 16F, 11 / 16F, 5, 1, 11, 15);
            horizontal(c, pose, light, 0xFFFFFFFF, 5 / 16F, 5 / 16F, 11 / 16F, 11 / 16F, 28 / 16F, 1, 0, 0, 6, 6);
        });
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(HEAD), (pose, c) -> {
            sides(c, pose, light, 0xFFFFFFFF, 3 / 16F, 28 / 16F, 3 / 16F, 13 / 16F, 32 / 16F, 13 / 16F, 0, 10, 10, 14);
            horizontal(c, pose, light, 0xFFFFFFFF, 3 / 16F, 3 / 16F, 13 / 16F, 13 / 16F, 32 / 16F, 1, 0, 0, 10, 10);
        });
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(BASE), (pose, c) ->
                horizontal(c, pose, light, 0xFFFFFFFF, 3 / 16F, 3 / 16F, 13 / 16F, 13 / 16F, 28 / 16F, -1,
                        3, 3, 13, 13));
        // Faction band: full-bright, sides tinted, caps untinted.
        int band = 0xFF000000 | state.bandRgb;
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(BAND), (pose, c) ->
                sides(c, pose, FULL_BRIGHT, band, 4 / 16F, 24 / 16F, 4 / 16F, 12 / 16F, 26 / 16F, 12 / 16F,
                        4, 7, 12, 9));
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(COLUMN), (pose, c) -> {
            horizontal(c, pose, FULL_BRIGHT, 0xFFFFFFFF, 4 / 16F, 4 / 16F, 12 / 16F, 12 / 16F, 26 / 16F, 1,
                    0, 8, 8, 16);
            horizontal(c, pose, FULL_BRIGHT, 0xFFFFFFFF, 4 / 16F, 4 / 16F, 12 / 16F, 12 / 16F, 24 / 16F, -1,
                    0, 8, 8, 16);
        });
        // Emitter ring: eight plates on an octagon around the head's waist.
        float yaw = state.ringYaw;
        int frame = state.ringFrame;
        collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(RING), (pose, c) -> {
            float cy = 30 / 16F;
            float r = 7.5F / 16F;
            float h = 2.8701F / 16F;
            float v = 1.4351F / 16F;
            float v0 = (frame * 16 + 4) / 96F;
            float v1 = (frame * 16 + 12) / 96F;
            for (int i = 0; i < 8; i++) {
                double theta = Math.toRadians(yaw + i * 45.0F);
                float cos = (float) Math.cos(theta);
                float sin = (float) Math.sin(theta);
                float cx = 0.5F + r * cos;
                float cz = 0.5F + r * sin;
                float tx = -sin;
                float tz = cos;
                quad(c, pose, FULL_BRIGHT, band, cos, 0, sin,
                        cx - h * tx, cy - v, cz - h * tz, 0, v1,
                        cx + h * tx, cy - v, cz + h * tz, 1, v1,
                        cx + h * tx, cy + v, cz + h * tz, 1, v0,
                        cx - h * tx, cy + v, cz - h * tz, 0, v0);
            }
        });
        if (state.groundRing) {
            submitGroundRing(state, poseStack, collector);
        }
    }

    private static void submitGroundRing(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float rg = state.radius + 0.5F;
        float inner = rg - 0.25F;
        float outer = rg + 0.25F;
        float y = 0.1F;
        // A single near-white texel of the band mask carries the vertex colour.
        float u = 8.5F / 16F;
        float v = 8.0F / 16F;
        float sweepDeg = state.sweep * 360.0F;
        boolean contested = state.contested;
        int base = state.baseArgb;
        int sweepColour = state.sweepArgb;
        int contestedColour = state.contestedArgb;
        collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(BAND), (pose, c) -> {
            for (int k = 0; k < GROUND_SEGMENTS; k++) {
                float from = k * 15.0F;
                float to = from + 15.0F;
                if (contested) {
                    segment(c, pose, inner, outer, y, from, to, contestedColour, u, v);
                } else if (to <= sweepDeg) {
                    segment(c, pose, inner, outer, y, from, to, sweepColour, u, v);
                } else if (from < sweepDeg) {
                    segment(c, pose, inner, outer, y, from, sweepDeg, sweepColour, u, v);
                    segment(c, pose, inner, outer, y, sweepDeg, to, base, u, v);
                } else {
                    segment(c, pose, inner, outer, y, from, to, base, u, v);
                }
            }
        });
    }

    /** One annulus segment; φ = 0 is north, growing clockwise seen from above. */
    private static void segment(VertexConsumer c, PoseStack.Pose pose, float inner, float outer, float y,
            float fromDeg, float toDeg, int argb, float u, float v) {
        double a0 = Math.toRadians(fromDeg);
        double a1 = Math.toRadians(toDeg);
        float s0 = (float) Math.sin(a0);
        float c0 = (float) -Math.cos(a0);
        float s1 = (float) Math.sin(a1);
        float c1 = (float) -Math.cos(a1);
        quad(c, pose, FULL_BRIGHT, argb, 0, 1, 0,
                0.5F + inner * s0, y, 0.5F + inner * c0, u, v,
                0.5F + inner * s1, y, 0.5F + inner * c1, u, v,
                0.5F + outer * s1, y, 0.5F + outer * c1, u, v,
                0.5F + outer * s0, y, 0.5F + outer * c0, u, v);
    }

    /** The four vertical faces of a box, each with the texel window (u0, v0)–(u1, v1). */
    private static void sides(VertexConsumer c, PoseStack.Pose pose, int light, int argb, float x0, float y0,
            float z0, float x1, float y1, float z1, float tu0, float tv0, float tu1, float tv1) {
        float u0 = tu0 / 16F;
        float v0 = tv0 / 16F;
        float u1 = tu1 / 16F;
        float v1 = tv1 / 16F;
        quad(c, pose, light, argb, 0, 0, -1, x1, y0, z0, u0, v1, x0, y0, z0, u1, v1, x0, y1, z0, u1, v0, x1, y1, z0,
                u0, v0); // north
        quad(c, pose, light, argb, 0, 0, 1, x0, y0, z1, u0, v1, x1, y0, z1, u1, v1, x1, y1, z1, u1, v0, x0, y1, z1,
                u0, v0); // south
        quad(c, pose, light, argb, -1, 0, 0, x0, y0, z0, u0, v1, x0, y0, z1, u1, v1, x0, y1, z1, u1, v0, x0, y1, z0,
                u0, v0); // west
        quad(c, pose, light, argb, 1, 0, 0, x1, y0, z1, u0, v1, x1, y0, z0, u1, v1, x1, y1, z0, u1, v0, x1, y1, z1,
                u0, v0); // east
    }

    /** A horizontal face at {@code y} ({@code ny} = 1 up, −1 down) with the texel window. */
    private static void horizontal(VertexConsumer c, PoseStack.Pose pose, int light, int argb, float x0, float z0,
            float x1, float z1, float y, float ny, float tu0, float tv0, float tu1, float tv1) {
        float u0 = tu0 / 16F;
        float v0 = tv0 / 16F;
        float u1 = tu1 / 16F;
        float v1 = tv1 / 16F;
        quad(c, pose, light, argb, 0, ny, 0, x0, y, z0, u0, v0, x0, y, z1, u0, v1, x1, y, z1, u1, v1, x1, y, z0,
                u1, v0);
    }

    /** One quad emitted both ways (visible from either side through any cull state). */
    private static void quad(VertexConsumer c, PoseStack.Pose pose, int light, int argb, float nx, float ny, float nz,
            float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu, float bv,
            float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv) {
        vertex(c, pose, light, argb, ax, ay, az, au, av, nx, ny, nz);
        vertex(c, pose, light, argb, bx, by, bz, bu, bv, nx, ny, nz);
        vertex(c, pose, light, argb, cx, cy, cz, cu, cv, nx, ny, nz);
        vertex(c, pose, light, argb, dx, dy, dz, du, dv, nx, ny, nz);
        vertex(c, pose, light, argb, dx, dy, dz, du, dv, -nx, -ny, -nz);
        vertex(c, pose, light, argb, cx, cy, cz, cu, cv, -nx, -ny, -nz);
        vertex(c, pose, light, argb, bx, by, bz, bu, bv, -nx, -ny, -nz);
        vertex(c, pose, light, argb, ax, ay, az, au, av, -nx, -ny, -nz);
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose pose, int light, int argb, float x, float y, float z,
            float u, float v, float nx, float ny, float nz) {
        c.addVertex(pose, x, y, z)
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
