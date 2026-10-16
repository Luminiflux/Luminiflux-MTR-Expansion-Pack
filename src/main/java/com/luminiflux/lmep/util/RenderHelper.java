package com.luminiflux.lmep.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.data.Rail;
import mtr.data.TransportMode;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * World-space drawing helpers for the ghost track and the node markers.
 *
 * <p>The ghost track is produced by handing MTR's own {@link Rail#render} a callback,
 * so the preview is built from exactly the same quads MTR would use for the finished
 * rail &mdash; same curve, same cable sag, same gauge. The rail width and height offset
 * per transport mode are the values MTR3 itself passes in
 * {@code mtr.render.RenderTrains#renderRailStandard}.</p>
 */
public final class RenderHelper {

    /**
     * MTR ships this texture for its quartz rails; it is a neutral two-stripe rail
     * strip, which makes it the natural "ghost" look for a preview.
     */
    public static final ResourceLocation RAIL_PREVIEW_TEXTURE =
            new ResourceLocation("mtr", "textures/block/rail_preview.png");

    /** Full-bright lightmap value, so the preview is readable in the dark. */
    public static final int FULL_LIGHT = LightTexture.FULL_BLOCK;

    // Rail height offsets (MTR: RenderTrains#renderRailStandard, parameter 3).
    private static final float Y_OFFSET_RAIL = 0.065625F;
    private static final float Y_OFFSET_CABLE = 0.253125F;

    // Rail half widths (MTR: RenderTrains#renderRailStandard, parameter 5).
    private static final float HALF_WIDTH_TRAIN = 1.0F;
    private static final float HALF_WIDTH_BOAT = 0.5F;
    private static final float HALF_WIDTH_CABLE = 0.25F;

    private RenderHelper() {
    }

    /** Vertical offset MTR lifts a rail of this transport mode by. */
    public static float railYOffset(TransportMode mode) {
        return mode == TransportMode.CABLE_CAR ? Y_OFFSET_CABLE : Y_OFFSET_RAIL;
    }

    /** Half width MTR draws a rail of this transport mode with. */
    public static float railHalfWidth(TransportMode mode) {
        if (mode == TransportMode.BOAT) {
            return HALF_WIDTH_BOAT;
        }
        if (mode == TransportMode.CABLE_CAR) {
            return HALF_WIDTH_CABLE;
        }
        return HALF_WIDTH_TRAIN;
    }

    /** MTR4-style pulse: scale the RGB channels with a ~1 Hz sine. */
    public static int flashingColor(int argb, boolean flash) {
        if (!flash) {
            return argb;
        }
        final double phase = 0.5D + 0.5D * Math.sin(System.currentTimeMillis() % 1000L / 1000.0D * Math.PI * 2.0D);
        final double factor = 0.55D + 0.45D * phase;
        final int a = (argb >>> 24) & 0xFF;
        final int r = (int) (((argb >>> 16) & 0xFF) * factor);
        final int g = (int) (((argb >>> 8) & 0xFF) * factor);
        final int b = (int) ((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Emits the ghost track for {@code rail}.
     *
     * <p>{@link Rail#render} walks the rail in ~1 block steps and calls back with the four
     * corners of each quad as {@code (x1, z1, x2, z2, x3, z3, x4, z4, yStart, yEnd)}.</p>
     *
     * @param halfWidth half width of the rail, from {@link #railHalfWidth(TransportMode)}
     * @param yOffset   vertical lift, from {@link #railYOffset(TransportMode)}
     */
    public static void renderGhostRail(VertexConsumer consumer, PoseStack.Pose pose, Rail rail,
                                       float halfWidth, float yOffset, int argb) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >>> 16) & 0xFF;
        final int g = (argb >>> 8) & 0xFF;
        final int b = argb & 0xFF;

        rail.render((x1, z1, x2, z2, x3, z3, x4, z4, yStart, yEnd) -> {
            final float yA = (float) (yStart + yOffset);
            final float yB = (float) (yEnd + yOffset);
            texturedVertex(consumer, pose, (float) x1, yA, (float) z1, 0.0F, 0.0F, r, g, b, a);
            texturedVertex(consumer, pose, (float) x2, yA, (float) z2, 1.0F, 0.0F, r, g, b, a);
            texturedVertex(consumer, pose, (float) x3, yB, (float) z3, 1.0F, 1.0F, r, g, b, a);
            texturedVertex(consumer, pose, (float) x4, yB, (float) z4, 0.0F, 1.0F, r, g, b, a);
        }, -halfWidth, halfWidth);
    }

    /** A single coloured line segment, for {@code RenderType.lines()}. */
    public static void drawLine(VertexConsumer consumer, PoseStack.Pose pose, Vec3 from, Vec3 to, int argb) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >>> 16) & 0xFF;
        final int g = (argb >>> 8) & 0xFF;
        final int b = argb & 0xFF;
        consumer.vertex(pose.pose(), (float) from.x, (float) from.y, (float) from.z)
                .color(r, g, b, a)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F);
        consumer.vertex(pose.pose(), (float) to.x, (float) to.y, (float) to.z)
                .color(r, g, b, a)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F);
    }

    /** Wireframe cube centred on a block, marking the anchor and the aimed node. */
    public static void drawBlockMarker(VertexConsumer consumer, PoseStack.Pose pose,
                                       double centerX, double centerY, double centerZ,
                                       double radius, int argb) {
        final Vec3[] corners = {
                new Vec3(centerX - radius, centerY - radius, centerZ - radius),
                new Vec3(centerX + radius, centerY - radius, centerZ - radius),
                new Vec3(centerX + radius, centerY - radius, centerZ + radius),
                new Vec3(centerX - radius, centerY - radius, centerZ + radius),
                new Vec3(centerX - radius, centerY + radius, centerZ - radius),
                new Vec3(centerX + radius, centerY + radius, centerZ - radius),
                new Vec3(centerX + radius, centerY + radius, centerZ + radius),
                new Vec3(centerX - radius, centerY + radius, centerZ + radius)
        };
        final int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (final int[] edge : edges) {
            drawLine(consumer, pose, corners[edge[0]], corners[edge[1]], argb);
        }
    }

    private static void texturedVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                       float x, float y, float z, float u, float v,
                                       int r, int g, int b, int a) {
        consumer.vertex(pose.pose(), x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_LIGHT)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F);
    }
}
