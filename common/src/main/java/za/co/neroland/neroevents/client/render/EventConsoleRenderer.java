package za.co.neroland.neroevents.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neroevents.client.EventColours;
import za.co.neroland.neroevents.client.widget.Hull;
import za.co.neroland.neroevents.console.EventConsoleBlock;
import za.co.neroland.neroevents.console.EventConsoleBlockEntity;
import za.co.neroland.neroevents.event.Severity;

/**
 * Draws the severity glyph of the most urgent event on the console's active screen, from the shared glyph sheet
 * ({@code textures/gui/event_glyphs.png}, so screen and GUI match pixel for pixel), full-bright, tinted by
 * severity and blinking 8 ticks on / 8 off with the screen strip. Nothing is drawn while the console is idle.
 *
 * <p>Culling: {@link #shouldRender} is a distance check (never a blanket {@code true}); the glyph is a 6-px
 * mark, pointless beyond {@link #DISTANCE} blocks. Geometry is emitted per facing, so no pose rotation is needed.
 * CLIENT-ONLY.
 */
public class EventConsoleRenderer implements BlockEntityRenderer<EventConsoleBlockEntity, EventConsoleRenderer.State> {

    private static final double DISTANCE = 32.0D;
    private static final float SIZE = 6.0F / 16.0F;
    private static final float CENTRE_Y = 9.0F / 16.0F;
    private static final float OUT = 0.5F / 16.0F + 0.002F;

    /** Render state: extracted from the block entity; nothing read at submit time. */
    public static class State extends BlockEntityRenderState {
        boolean visible;
        int slot;
        int argb;
        Direction facing = Direction.NORTH;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public int getViewDistance() {
        return (int) DISTANCE;
    }

    @Override
    public boolean shouldRender(EventConsoleBlockEntity console, Vec3 cameraPos) {
        return console.severity() != EventConsoleBlockEntity.NONE
                && Vec3.atCenterOf(console.getBlockPos()).closerThan(cameraPos, DISTANCE);
    }

    @Override
    public void extractRenderState(EventConsoleBlockEntity console, State state, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(console, state, partialTick, cameraPos, breakProgress);
        Level level = console.getLevel();
        BlockState block = console.getBlockState();
        byte severity = console.severity();
        state.visible = level != null && severity != EventConsoleBlockEntity.NONE
                && block.hasProperty(EventConsoleBlock.ACTIVE) && block.getValue(EventConsoleBlock.ACTIVE)
                && Vec3.atCenterOf(console.getBlockPos()).closerThan(cameraPos, DISTANCE)
                && (level.getGameTime() / 8L) % 2L == 0L;
        if (!state.visible) {
            return;
        }
        Severity sev = EventColours.severityOf(severity);
        state.slot = EventColours.glyphSlot(sev);
        state.argb = EventColours.opaque(EventColours.severity(sev));
        state.facing = block.hasProperty(EventConsoleBlock.FACING) ? block.getValue(EventConsoleBlock.FACING)
                : Direction.NORTH;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.visible) {
            return;
        }
        float u0 = state.slot * 8.0F / 64.0F;
        float u1 = (state.slot + 1) * 8.0F / 64.0F;
        int argb = state.argb;
        Direction facing = state.facing;
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(Hull.GLYPHS), (pose, c) -> {
            // Face centre, the viewer's "right" along the face, and the outward normal.
            float cx;
            float cz;
            float rx;
            float rz;
            float nx;
            float nz;
            switch (facing) {
                case SOUTH -> {
                    cx = 0.5F;
                    cz = 1.0F + OUT;
                    rx = 1.0F;
                    rz = 0.0F;
                    nx = 0.0F;
                    nz = 1.0F;
                }
                case EAST -> {
                    cx = 1.0F + OUT;
                    cz = 0.5F;
                    rx = 0.0F;
                    rz = -1.0F;
                    nx = 1.0F;
                    nz = 0.0F;
                }
                case WEST -> {
                    cx = -OUT;
                    cz = 0.5F;
                    rx = 0.0F;
                    rz = 1.0F;
                    nx = -1.0F;
                    nz = 0.0F;
                }
                default -> {
                    cx = 0.5F;
                    cz = -OUT;
                    rx = -1.0F;
                    rz = 0.0F;
                    nx = 0.0F;
                    nz = -1.0F;
                }
            }
            float h = SIZE / 2.0F;
            float y0 = CENTRE_Y - h;
            float y1 = CENTRE_Y + h;
            float lx = cx - rx * h;
            float lz = cz - rz * h;
            float px = cx + rx * h;
            float pz = cz + rz * h;
            vertex(c, pose, lx, y1, lz, u0, 0.0F, nx, nz, argb);
            vertex(c, pose, lx, y0, lz, u0, 0.5F, nx, nz, argb);
            vertex(c, pose, px, y0, pz, u1, 0.5F, nx, nz, argb);
            vertex(c, pose, px, y1, pz, u1, 0.0F, nx, nz, argb);
            // The back side too, so the cull state never hides the mark.
            vertex(c, pose, px, y1, pz, u1, 0.0F, -nx, -nz, argb);
            vertex(c, pose, px, y0, pz, u1, 0.5F, -nx, -nz, argb);
            vertex(c, pose, lx, y0, lz, u0, 0.5F, -nx, -nz, argb);
            vertex(c, pose, lx, y1, lz, u0, 0.0F, -nx, -nz, argb);
        });
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose pose, float x, float y, float z, float u, float v,
            float nx, float nz, int argb) {
        c.addVertex(pose, x, y, z)
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(EventColours.FULL_BRIGHT)
                .setNormal(pose, nx, 0.0F, nz);
    }
}
