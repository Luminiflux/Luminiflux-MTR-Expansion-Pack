package com.luminiflux.lmep.util;

import mtr.data.Rail;
import mtr.data.RailType;
import mtr.item.ItemRailModifier;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the handful of MTR3 internals that LMEP needs but that MTR does not expose.
 *
 * <p>Everything here was verified against the real 1.20.1 Forge release jar by
 * disassembly, so the field names below are exact:</p>
 * <ul>
 *   <li>{@code ItemRailModifier.isOneWay} / {@code ItemRailModifier.railType}
 *       &mdash; private final, no getters. {@code railType} is {@code null} for the
 *       rail <em>remover</em>, which is how LMEP tells the two apart.</li>
 *   <li>{@code Rail.r1} / {@code Rail.r2} &mdash; private final doubles holding the
 *       horizontal radius of each half of the rail.</li>
 * </ul>
 *
 * <p>Every lookup degrades gracefully: if MTR ever changes, LMEP simply stops showing
 * the preview instead of crashing.</p>
 */
public final class MtrRailAccess {

    private static final double[] NO_RADII = {0.0D, 0.0D};

    private static final Map<ItemRailModifier, RailItemInfo> ITEM_CACHE = new ConcurrentHashMap<>();

    private static Field railTypeField;
    private static Field oneWayField;
    private static Field radius1Field;
    private static Field radius2Field;

    private static boolean itemLookupBroken;
    private static boolean radiusLookupBroken;

    private MtrRailAccess() {
    }

    /**
     * @return the rail type / one-way flag of a connector item, or {@code null} when
     *         the item is not a usable connector (rail remover, or reflection failed).
     */
    public static RailItemInfo infoOf(ItemRailModifier item) {
        if (item == null) {
            return null;
        }
        final RailItemInfo cached = ITEM_CACHE.get(item);
        if (cached != null) {
            return cached;
        }
        if (itemLookupBroken) {
            return null;
        }
        try {
            if (railTypeField == null) {
                railTypeField = ItemRailModifier.class.getDeclaredField("railType");
                railTypeField.setAccessible(true);
            }
            if (oneWayField == null) {
                oneWayField = ItemRailModifier.class.getDeclaredField("isOneWay");
                oneWayField.setAccessible(true);
            }
            final RailItemInfo info = new RailItemInfo(
                    (RailType) railTypeField.get(item),
                    oneWayField.getBoolean(item)
            );
            ITEM_CACHE.put(item, info);
            return info;
        } catch (final ReflectiveOperationException | RuntimeException broken) {
            itemLookupBroken = true;
            return null;
        }
    }

    /**
     * @return {@code {r1, r2}} of the rail, or {@code {0, 0}} when unavailable.
     *         A radius of 0 together with a straight segment simply means "no curve".
     */
    public static double[] radiiOf(Rail rail) {
        if (rail == null || radiusLookupBroken) {
            return NO_RADII;
        }
        try {
            if (radius1Field == null) {
                radius1Field = Rail.class.getDeclaredField("r1");
                radius1Field.setAccessible(true);
            }
            if (radius2Field == null) {
                radius2Field = Rail.class.getDeclaredField("r2");
                radius2Field.setAccessible(true);
            }
            return new double[]{radius1Field.getDouble(rail), radius2Field.getDouble(rail)};
        } catch (final ReflectiveOperationException | RuntimeException broken) {
            radiusLookupBroken = true;
            return NO_RADII;
        }
    }

    /** Immutable snapshot of the two private fields of a connector item. */
    public static final class RailItemInfo {

        /** {@code null} for the rail remover. */
        public final RailType railType;
        public final boolean oneWay;

        RailItemInfo(RailType railType, boolean oneWay) {
            this.railType = railType;
            this.oneWay = oneWay;
        }
    }
}
