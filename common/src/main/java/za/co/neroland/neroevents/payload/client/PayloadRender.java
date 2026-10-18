package za.co.neroland.neroevents.payload.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * Client-only render helpers shared by the payload entity renderers: the one place the 26.3 submit and
 * rotation signatures are branched, the full-bright light value, texture ids, and a CPU box emitter for the
 * meteor's UV-scrolled heat pass (the nerosecurity custom-geometry recipe).
 */
final class PayloadRender {

    /** Packed full-bright light (emissive layers). */
    static final int FULL_BRIGHT = 0x00F000F0;
    /** REWARD gold, the pod beacon tint. */
    static final int REWARD_RGB = 0xF4C542;

    private PayloadRender() {
    }

    static Identifier entityTexture(String name) {
        return Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "textures/entity/" + name + ".png");
    }

    static float rad(float degrees) {
        return degrees * ((float) Math.PI / 180.0F);
    }

    /** {@code 0xAARRGGBB} from an RGB and an alpha 0..1. */
    static int argb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    static <S extends EntityRenderState> void submitModel(SubmitNodeCollector collector, EntityModel<S> model, S state,
            PoseStack poseStack, RenderType renderType, int light, int color) {
        //? if >=26.3 {
        /*collector.order(0).submitModel(model, state, poseStack, renderType, light, OverlayTexture.NO_OVERLAY, color);
        *///?} else {
        collector.order(0).submitModel(model, state, poseStack, renderType, light, OverlayTexture.NO_OVERLAY, color,
                null, 0, null);
        //?}
    }

    static void rotateY(PoseStack poseStack, float degrees) {
        //? if >=26.3 {
        /*poseStack.rotate(Axis.YP.rotationDegrees(degrees));
        *///?} else {
        poseStack.mulPose(Axis.YP.rotationDegrees(degrees));
        //?}
    }

    static void rotateX(PoseStack poseStack, float degrees) {
        //? if >=26.3 {
        /*poseStack.rotate(Axis.XP.rotationDegrees(degrees));
        *///?} else {
        poseStack.mulPose(Axis.XP.rotationDegrees(degrees));
        //?}
    }

    static void rotateZ(PoseStack poseStack, float degrees) {
        //? if >=26.3 {
        /*poseStack.rotate(Axis.ZP.rotationDegrees(degrees));
        *///?} else {
        poseStack.mulPose(Axis.ZP.rotationDegrees(degrees));
        //?}
    }

    /** One complete vertex. */
    static void vertex(VertexConsumer c, PoseStack.Pose pose, float x, float y, float z, float u, float v,
            float nx, float ny, float nz, int argb, int light) {
        c.addVertex(pose, x, y, z)
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /** A one-sided quad a→b→c→d with UV (u0,v0)-(u1,v1). */
    static void quad(VertexConsumer c, PoseStack.Pose pose, float nx, float ny, float nz,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1, int argb, int light) {
        vertex(c, pose, ax, ay, az, u0, v0, nx, ny, nz, argb, light);
        vertex(c, pose, bx, by, bz, u1, v0, nx, ny, nz, argb, light);
        vertex(c, pose, cx, cy, cz, u1, v1, nx, ny, nz, argb, light);
        vertex(c, pose, dx, dy, dz, u0, v1, nx, ny, nz, argb, light);
    }

    /**
     * A centred box of half-extents (hx, hy, hz) whose every face maps the whole texture width (u 0..1) and a
     * quarter of its height starting at {@code v0} — the heat strip, scrolled by moving {@code v0}.
     */
    static void scrolledBox(VertexConsumer c, PoseStack.Pose pose, float hx, float hy, float hz, float v0,
            int argb, int light) {
        float v1 = v0 + 0.25F;
        quad(c, pose, 0, 1, 0, -hx, hy, -hz, -hx, hy, hz, hx, hy, hz, hx, hy, -hz, 0, v0, 1, v1, argb, light);
        quad(c, pose, 0, -1, 0, -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz, 0, v0, 1, v1, argb, light);
        quad(c, pose, 0, 0, -1, hx, hy, -hz, -hx, hy, -hz, -hx, -hy, -hz, hx, -hy, -hz, 0, v0, 1, v1, argb, light);
        quad(c, pose, 0, 0, 1, -hx, hy, hz, hx, hy, hz, hx, -hy, hz, -hx, -hy, hz, 0, v0, 1, v1, argb, light);
        quad(c, pose, -1, 0, 0, -hx, hy, -hz, -hx, hy, hz, -hx, -hy, hz, -hx, -hy, -hz, 0, v0, 1, v1, argb, light);
        quad(c, pose, 1, 0, 0, hx, hy, hz, hx, hy, -hz, hx, -hy, -hz, hx, -hy, hz, 0, v0, 1, v1, argb, light);
    }

    /** Two crossed vertical double-sided quads of {@code size} blocks, one animation frame of a 4-frame strip. */
    static void crossedSprite(VertexConsumer c, PoseStack.Pose pose, float size, int frame, int argb, int light) {
        float h = size / 2.0F;
        float v0 = (frame & 3) * 0.25F;
        float v1 = v0 + 0.25F;
        quad(c, pose, 0, 0, -1, -h, size, 0, h, size, 0, h, 0, 0, -h, 0, 0, 0, v0, 1, v1, argb, light);
        quad(c, pose, 0, 0, 1, h, size, 0, -h, size, 0, -h, 0, 0, h, 0, 0, 0, v0, 1, v1, argb, light);
        quad(c, pose, -1, 0, 0, 0, size, -h, 0, size, h, 0, 0, h, 0, 0, -h, 0, v0, 1, v1, argb, light);
        quad(c, pose, 1, 0, 0, 0, size, h, 0, size, -h, 0, 0, -h, 0, 0, h, 0, v0, 1, v1, argb, light);
    }
}
