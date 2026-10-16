package com.luminiflux.lmep.preview;

import com.luminiflux.lmep.LmepConfig;
import com.luminiflux.lmep.LmepKeybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Screen-space companion to {@link TrackPreviewRenderer}, anchored to the bottom-left
 * corner.
 *
 * <p>The panel is deliberately <em>not</em> always on screen. It only appears while a rail
 * connector is in hand ({@link PreviewState#holdingConnector}), because that is the only
 * situation in which it can say anything useful. Once a connector is held it either shows
 * the full readout or, before an anchor has been picked, a one-line prompt.</p>
 *
 * <p>The floating world labels are easy to lose against terrain, so the same numbers are
 * repeated here, plus the two values with no room in the world: the true 3D length next to
 * the flat length, and the accumulated length of the line the anchor belongs to.</p>
 */
public final class TrackPreviewHud {

    private static final int PANEL_BACKGROUND = 0xB0000000;
    private static final int PANEL_BORDER = 0xFF4A4A4A;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFFAAAAAA;
    private static final int COLOR_VALID = 0xFF55FF55;
    private static final int COLOR_INVALID = 0xFFFF5555;

    private static final int MARGIN = 8;
    private static final int PADDING = 6;

    private TrackPreviewHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!LmepConfig.showHud()) {
            return;
        }
        final PreviewState state = TrackPreviewManager.STATE;
        if (!state.holdingConnector) {
            return; // Nothing is in hand that could use a preview.
        }

        final GuiGraphics guiGraphics = event.getGuiGraphics();
        final Font font = Minecraft.getInstance().font;
        final int bottom = event.getWindow().getGuiScaledHeight() - MARGIN;

        if (!state.active) {
            final String prompt = LmepConfig.enabled()
                    ? Component.translatable("lmep.hud.ready").getString()
                    : Component.translatable("lmep.hud.disabled",
                            keyName(LmepKeybinds.TOGGLE_PREVIEW)).getString();
            drawPanel(guiGraphics, font, MARGIN, bottom,
                    new String[]{prompt}, new int[]{COLOR_HINT});
            return;
        }

        final String status = state.valid
                ? Component.translatable("lmep.hud.status_valid").getString()
                : Component.translatable("lmep.hud.status_invalid",
                        Component.translatable(state.invalidReason).getString()).getString();

        final String[] lines = {
                Component.translatable("lmep.hud.title").getString(),
                status,
                Component.translatable("lmep.hud.length",
                        String.format("%.2f m  (%.2f m flat)", state.length3D, state.lengthXZ)).getString(),
                Component.translatable("lmep.hud.slope",
                        String.format("%+.1f%%", state.slopePercent)).getString(),
                Component.translatable("lmep.hud.radius", state.formatRadii()).getString(),
                Component.translatable("lmep.hud.cumulative",
                        String.format("%.1f m  (%d segments)",
                                state.cumulativeLength, state.cumulativeSegments)).getString(),
                Component.translatable("lmep.hud.hint_node").getString()
        };
        final int[] colors = {
                COLOR_TEXT,
                state.valid ? COLOR_VALID : COLOR_INVALID,
                COLOR_TEXT,
                COLOR_TEXT,
                COLOR_TEXT,
                COLOR_TEXT,
                COLOR_HINT
        };

        drawPanel(guiGraphics, font, MARGIN, bottom, lines, colors);
    }

    /**
     * Draws a bordered panel whose bottom-left corner sits at {@code (left, bottom)}.
     * Laying it out from the bottom up keeps the corner anchored as the line count changes.
     */
    private static void drawPanel(GuiGraphics guiGraphics, Font font, int left, int bottom,
                                  String[] lines, int[] colors) {
        int panelWidth = 0;
        for (final String line : lines) {
            panelWidth = Math.max(panelWidth, font.width(line));
        }
        panelWidth += PADDING * 2;

        final int lineHeight = font.lineHeight + 2;
        final int panelHeight = lines.length * lineHeight + PADDING * 2 - 2;
        final int top = bottom - panelHeight;
        final int right = left + panelWidth;

        guiGraphics.fill(left, top, right, top + panelHeight, PANEL_BACKGROUND);
        guiGraphics.fill(left, top, right, top + 1, PANEL_BORDER);
        guiGraphics.fill(left, top, left + 1, top + panelHeight, PANEL_BORDER);
        guiGraphics.fill(right - 1, top, right, top + panelHeight, PANEL_BORDER);
        guiGraphics.fill(left, top + panelHeight - 1, right, top + panelHeight, PANEL_BORDER);

        int y = top + PADDING;
        for (int i = 0; i < lines.length; i++) {
            guiGraphics.drawString(font, lines[i], left + PADDING, y, colors[i]);
            y += lineHeight;
        }
    }

    private static String keyName(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString();
    }
}
