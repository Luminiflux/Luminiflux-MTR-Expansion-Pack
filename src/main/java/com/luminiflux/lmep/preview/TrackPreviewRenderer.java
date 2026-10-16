package com.luminiflux.lmep.preview;

import com.luminiflux.lmep.LmepConfig;
import com.luminiflux.lmep.util.RenderHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.data.Rail;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Draws the ghost track and the floating readouts in the world.
 *
 * <p>{@code RenderLevelStageEvent} does not hand out a {@link MultiBufferSource}, so one
 * is created around the shared {@link Tesselator} buffer and flushed at the end of the
 * frame's stage. Drawing after translucent blocks means the ghost track blends against
 * the finished world instead of being overwritten by it.</p>
 */
public final class TrackPreviewRenderer {

    private static final int COLOR_VALID = 0xFF55FF55;
    private static final int COLOR_INVALID = 0xFFFF5555;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_ANCHOR = 0xFFFFAA00;

    private static final double NODE_MARKER_RADIUS = 0.45D;

    private TrackPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        final PreviewState state = TrackPreviewManager.STATE;
        if (!state.active || state.ghostRail == null || !LmepConfig.showWorldPreview()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        final PoseStack poseStack = event.getPoseStack();
        final Camera camera = event.getCamera();
        final MultiBufferSource.BufferSource buffers =
                MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

        try {
            final int baseColor = state.valid ? COLOR_VALID : COLOR_INVALID;

            renderGhostRail(state, poseStack, buffers, baseColor);

            final VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            RenderHelper.drawBlockMarker(lines, poseStack.last(),
                    state.anchor.getX() + 0.5D, state.anchor.getY() + 0.5D, state.anchor.getZ() + 0.5D,
                    NODE_MARKER_RADIUS, COLOR_ANCHOR);
            RenderHelper.drawBlockMarker(lines, poseStack.last(),
                    state.cursor.getX() + 0.5D, state.cursor.getY() + 0.5D, state.cursor.getZ() + 0.5D,
                    NODE_MARKER_RADIUS, baseColor);

            if (LmepConfig.showWorldText()) {
                renderWorldText(state, poseStack, buffers, camera, minecraft.font, baseColor);
            }
        } finally {
            buffers.endBatch();
        }
    }

    private static void renderGhostRail(PreviewState state, PoseStack poseStack,
                                        MultiBufferSource buffers, int baseColor) {
        final int alpha = (int) Math.round(Math.max(0.0D, Math.min(1.0D, LmepConfig.ghostAlpha())) * 255.0D);
        final int tinted = (alpha << 24) | (baseColor & 0x00FFFFFF);
        final int color = RenderHelper.flashingColor(tinted, LmepConfig.flashEnabled());

        final float halfWidth = (float) (RenderHelper.railHalfWidth(state.transportMode)
                * LmepConfig.ghostWidthScale());
        final float yOffset = RenderHelper.railYOffset(state.transportMode);

        final VertexConsumer quads = buffers.getBuffer(
                RenderType.entityTranslucent(RenderHelper.RAIL_PREVIEW_TEXTURE));
        RenderHelper.renderGhostRail(quads, poseStack.last(), state.ghostRail, halfWidth, yOffset, color);
    }

    private static void renderWorldText(PreviewState state, PoseStack poseStack,
                                        MultiBufferSource buffers, Camera camera, Font font,
                                        int baseColor) {
        // Aimed node: the numbers the builder cares about while dragging the track.
        final Vec3 cursorLabel = new Vec3(
                state.cursor.getX() + 0.5D, state.cursor.getY() + 1.15D, state.cursor.getZ() + 0.5D);
        drawBillboard(font, poseStack, buffers, camera, cursorLabel,
                Component.translatable("lmep.world.length", String.format("%.2f", state.length3D)).getString(),
                COLOR_TEXT, 0.0F);
        drawBillboard(font, poseStack, buffers, camera, cursorLabel,
                String.format("Δ %+d %+d %+d",
                        (int) state.deltaX, (int) state.deltaY, (int) state.deltaZ),
                COLOR_TEXT, -0.30F);
        drawBillboard(font, poseStack, buffers, camera, cursorLabel,
                Component.translatable("lmep.world.slope", String.format("%+.1f%%", state.slopePercent)).getString(),
                COLOR_TEXT, -0.60F);
        drawBillboard(font, poseStack, buffers, camera, cursorLabel,
                Component.translatable("lmep.world.radius", state.formatRadii()).getString(),
                COLOR_TEXT, -0.90F);
        if (!state.valid) {
            drawBillboard(font, poseStack, buffers, camera, cursorLabel,
                    Component.translatable(state.invalidReason).getString(), COLOR_INVALID, -1.20F);
        }

        // Anchor: what it is, and how long the line it belongs to already is.
        final Vec3 anchorLabel = new Vec3(
                state.anchor.getX() + 0.5D, state.anchor.getY() + 1.15D, state.anchor.getZ() + 0.5D);
        drawBillboard(font, poseStack, buffers, camera, anchorLabel,
                Component.translatable("lmep.world.anchor").getString(), COLOR_ANCHOR, 0.0F);
        if (LmepConfig.showCumulativeLength()) {
            drawBillboard(font, poseStack, buffers, camera, anchorLabel,
                    Component.translatable("lmep.world.cumulative",
                            String.format("%.1f", state.cumulativeLength)).getString(),
                    COLOR_TEXT, -0.30F);
        }
    }

    /**
     * Camera-facing world text.
     *
     * <p>Uses {@link Camera#rotation()} exactly like vanilla's name tag rendering, so the
     * text always faces the player without hand-rolled rotation order.</p>
     */
    private static void drawBillboard(Font font, PoseStack poseStack, MultiBufferSource buffers,
                                      Camera camera, Vec3 worldPosition, String text,
                                      int color, float lineOffset) {
        final Vec3 cameraPosition = camera.getPosition();
        poseStack.pushPose();
        poseStack.translate(
                worldPosition.x - cameraPosition.x,
                worldPosition.y - cameraPosition.y + lineOffset,
                worldPosition.z - cameraPosition.z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, color, false,
                poseStack.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET,
                LightTexture.FULL_BLOCK, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
