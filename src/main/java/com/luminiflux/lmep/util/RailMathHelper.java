package com.luminiflux.lmep.util;

import mtr.data.Rail;
import net.minecraft.world.phys.Vec3;

/**
 * Rail geometry helpers.
 *
 * <p>MTR's {@link Rail#getPosition(double)} takes a distance in the same unit as
 * {@link Rail#getLength()}, i.e. the <em>horizontal</em> arc length of the rail, and
 * clamps it to {@code [0, getLength()]}. Sampling it is therefore the correct way to
 * measure the real, sloped 3D length of a track &mdash; which is what a player wants to
 * see when planning a line, and what MTR itself never displays.</p>
 */
public final class RailMathHelper {

    /** Samples taken per block of rail. */
    private static final double SAMPLES_PER_BLOCK = 4.0D;
    private static final int MIN_STEPS = 4;
    private static final int MAX_STEPS = 512;

    private RailMathHelper() {
    }

    /** True length along the rail, including vertical rise. */
    public static double trueLength3D(Rail rail) {
        return sample(rail, true);
    }

    /** Length of the rail projected onto the XZ plane. */
    public static double lengthXZ(Rail rail) {
        return sample(rail, false);
    }

    /**
     * Cheap 3D length: the horizontal arc length combined with the total vertical rise.
     *
     * <p>Only two {@link Rail#getPosition(double)} calls, so it is suitable for summing
     * hundreds of segments when walking a whole line, where {@link #trueLength3D(Rail)}
     * would be far too expensive. MTR rails are gentle enough that the difference is
     * well under a percent.</p>
     */
    public static double approxLength3D(Rail rail) {
        if (rail == null) {
            return 0.0D;
        }
        final double paramLength = rail.getLength();
        if (!(paramLength > 0.0D)) {
            return 0.0D;
        }
        final double rise = rail.getPosition(paramLength).y - rail.getPosition(0.0D).y;
        return Math.sqrt(paramLength * paramLength + rise * rise);
    }

    private static double sample(Rail rail, boolean includeY) {
        if (rail == null) {
            return 0.0D;
        }
        final double paramLength = rail.getLength();
        if (!(paramLength > 0.0D)) {
            return 0.0D;
        }
        final int steps = (int) Math.max(MIN_STEPS,
                Math.min(MAX_STEPS, Math.round(paramLength * SAMPLES_PER_BLOCK)));

        Vec3 previous = rail.getPosition(0.0D);
        double total = 0.0D;
        for (int i = 1; i <= steps; i++) {
            final Vec3 current = rail.getPosition(paramLength * i / steps);
            final double dx = current.x - previous.x;
            final double dz = current.z - previous.z;
            if (includeY) {
                final double dy = current.y - previous.y;
                total += Math.sqrt(dx * dx + dy * dy + dz * dz);
            } else {
                total += Math.sqrt(dx * dx + dz * dz);
            }
            previous = current;
        }
        return total;
    }
}
