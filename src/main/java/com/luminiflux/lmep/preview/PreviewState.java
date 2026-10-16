package com.luminiflux.lmep.preview;

import mtr.data.Rail;
import mtr.data.RailAngle;
import mtr.data.RailType;
import mtr.data.TransportMode;
import net.minecraft.core.BlockPos;

/**
 * Immutable-per-frame snapshot of what the preview should draw.
 *
 * <p>Recomputed once per client tick by {@link TrackPreviewManager}; the renderer and
 * the HUD only ever read from it.</p>
 */
public final class PreviewState {

    /**
     * True while a usable rail connector is held in either hand, whether or not an anchor
     * has been selected yet. The HUD shows nothing at all unless this is set, which is
     * what keeps the panel from being permanently on screen.
     */
    public boolean holdingConnector = false;

    /** True while a connector with a valid anchor is held and a node is being aimed at. */
    public boolean active = false;
    /** True when MTR would actually accept the connection. */
    public boolean valid = false;
    /** Translation key of the reason, only meaningful while {@link #active} and not valid. */
    public String invalidReason = "";

    /** First node of the connection (stored in the item's NBT by MTR). */
    public BlockPos anchor;
    /** Node currently under the crosshair; the second node of the connection. */
    public BlockPos cursor;
    /** The rail MTR would create, running from {@link #cursor} to {@link #anchor}. */
    public Rail ghostRail;

    public RailAngle facingStart;
    public RailAngle facingEnd;

    /** The rail type MTR would actually store (cable-car modes resolve this dynamically). */
    public RailType railType;
    public boolean oneWay;
    public TransportMode transportMode;

    /** {@code Rail#getLength()}, i.e. the horizontal arc length. */
    public double lengthParam;
    /** True length along the rail including the vertical rise. */
    public double length3D;
    /** Horizontal projection of the rail. */
    public double lengthXZ;
    public double deltaX;
    public double deltaY;
    public double deltaZ;
    public double slopePercent;
    /** Horizontal radius of the two halves of the rail. */
    public double radiusStart;
    public double radiusEnd;

    /** Length of the longest existing line reachable from the anchor, plus this preview. */
    public double cumulativeLength;
    public int cumulativeSegments;

    public boolean isInvalid() {
        return active && !valid;
    }

    /** Human readable radius: one value when both halves match, otherwise "r1 / r2". */
    public String formatRadii() {
        if (radiusStart <= 0.0D && radiusEnd <= 0.0D) {
            return "-";
        }
        if (radiusStart <= 0.0D || radiusEnd <= 0.0D || Math.abs(radiusStart - radiusEnd) < 0.001D) {
            return String.format("%.1f", radiusStart > 0.0D ? radiusStart : radiusEnd);
        }
        return String.format("%.1f / %.1f", radiusStart, radiusEnd);
    }
}
