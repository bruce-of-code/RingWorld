package dev.ringworld.world;

/**
 * Orbital day cycle maths (experiment branch, 2026-11-22). Pure and loader-neutral.
 *
 * <p>Model: the star is far away in a fixed direction {@link #TILT_DEGREES} out of the ring's
 * plane, and the ring spins once per Minecraft day. A point on the inner surface is in day
 * when that surface faces the star, so half the ring is lit and the terminator sweeps around
 * at {@code circumference / 24000} blocks per tick.
 *
 * <p>Everything reduces to one rule: the local time at intrinsic X is the world clock minus
 * the longitude's share of a day,
 * <pre>    local = world - (wrapX(x) / C) * 24000</pre>
 * With that, vanilla's own sky conventions are physically right on the ring: the sun and
 * stars rotate about world Z (vanilla's sky axis, which is the ring's axis here), the sun
 * rises toward +X, and with the 15 degree tilt applied toward +Z its local elevation is
 * {@code asin(cos(tilt) * cos(2 pi (local - 6000) / 24000))}, which is exactly the elevation of
 * the fixed physical star seen from that point of the spinning ring (see RingOrbitalDayTest).
 *
 * <p>Why 15 degrees: edge-on, the far side of the ring would eclipse the star at every noon;
 * the band only spans about +/-1.5 degrees seen from across the ring, so any tilt clears it.
 * Near the horizon the star is hidden by the floor curving up and by the rim on its side, at
 * about 9 degrees elevation mid-band with 15 degrees of tilt versus 10.5 with 10. Noon height
 * is 90 - tilt.
 */
public final class RingOrbitalDay {
    public static final double TILT_DEGREES = 15.0;
    public static final double TILT_RADIANS = Math.toRadians(TILT_DEGREES);
    public static final long DAY_TICKS = 24_000L;

    private RingOrbitalDay() { }

    /** Ticks to subtract from the world clock at this longitude, in [0, DAY_TICKS). */
    public static long longitudeOffsetTicks(RingGeometry geometry, double blockX) {
        long offset = Math.round(geometry.wrapX(blockX) / geometry.circumferenceBlocks() * DAY_TICKS);
        return Math.floorMod(offset, DAY_TICKS);
    }

    /** Local clock at this longitude; never negative, so day counts stay sane in a new world. */
    public static long localTicks(RingGeometry geometry, long worldTicks, double blockX) {
        long local = worldTicks - longitudeOffsetTicks(geometry, blockX);
        return local >= 0 ? local : local + DAY_TICKS;
    }

    /** Sine of the star's elevation at this longitude for the given world time (ticks may be fractional). */
    public static double sunElevationSine(RingGeometry geometry, double worldTicks, double blockX) {
        double local = worldTicks - (double)longitudeOffsetTicks(geometry, blockX);
        return Math.cos(TILT_RADIANS) * Math.cos(2.0 * Math.PI * (local - 6000.0) / DAY_TICKS);
    }
}
