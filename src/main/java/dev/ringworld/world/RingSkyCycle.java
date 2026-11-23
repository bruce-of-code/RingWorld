package dev.ringworld.world;

/**
 * Visual phase of the fixed ringworld sun.
 *
 * <p>Vanilla still owns the authoritative 24,000-tick daylight clock and all
 * gameplay lighting. RingWorld keeps the sun stationary, then varies only its
 * apparent intensity and colour so the celestial visual follows that same
 * global clock without a physical shadow-panel array.</p>
 */
public final class RingSkyCycle {
    public static final long DAY_LENGTH_TICKS = 24_000L;
    public static final float FIXED_SUN_ANGLE_RADIANS = 0.0F;
    /** One tenth of vanilla's padded 30-unit sun quad. */
    public static final float SUN_HALF_WIDTH = 3.0F;
    /** Fraction of vanilla's padded sun quad occupied by the bright disc. */
    public static final float SUN_VISIBLE_TEXTURE_SCALE = 0.2625F;
    /** Angular radius of the visible disc inside vanilla's padded sun sprite. */
    public static final float SUN_ANGULAR_HALF_WIDTH_DEGREES =
            (float)Math.toDegrees(Math.atan((SUN_HALF_WIDTH * SUN_VISIBLE_TEXTURE_SCALE) / 100.0F));
    public static final float SUN_RENDER_DISTANCE = 100.0F;

    private RingSkyCycle() { }

    /**
     * Rotation that expresses one inertially fixed star field in the player's
     * local tangent frame. Moving around the ring rotates local up, so the sky
     * applies the equal and opposite rotation instead of following the player.
     */
    public static float starFieldAngleRadians(RingGeometry geometry, double cameraX) {
        if (geometry == null) throw new IllegalArgumentException("ring geometry is required");
        return (float)-geometry.angleAt(cameraX);
    }

    /**
     * Smoothly interpolates four familiar Minecraft lighting keyframes:
     * warm dawn, neutral noon, warm dusk, and cool near-dark midnight.
     */
    public static SunVisual sunVisual(double timeOfDay) {
        double phase = wrapDayTime(timeOfDay);
        if (phase < 6_000.0) {
            return interpolate(DAWN, NOON, (float)(phase / 6_000.0));
        }
        if (phase < 12_000.0) {
            return interpolate(NOON, DUSK, (float)((phase - 6_000.0) / 6_000.0));
        }
        if (phase < 18_000.0) {
            return interpolate(DUSK, MIDNIGHT, (float)((phase - 12_000.0) / 6_000.0));
        }
        return interpolate(MIDNIGHT, DAWN, (float)((phase - 18_000.0) / 6_000.0));
    }

    /** Sun elevation (sine) below which the orbital sun starts to redden: about 6 degrees. */
    public static final double ORBITAL_WARM_ELEVATION_SINE = 0.10;

    /**
     * Orbital day: colour from the star's local elevation, not the time-of-day curve. The ring's
     * air is a thin layer held in by the rims, and sunlight only crosses a long path of it right
     * at the horizon, so the warm tint is confined to the last few degrees (vanilla-like time
     * keyframes kept the disc orange up to ~45 degrees). Below the horizon the star is only
     * seen past the band's edge, still through that low path, so it stays warm and dim.
     */
    public static SunVisual orbitalSunVisual(double localTimeOfDay) {
        double elevation = Math.cos(RingOrbitalDay.TILT_RADIANS)
                * Math.cos(2.0 * Math.PI * (wrapDayTime(localTimeOfDay) - 6_000.0) / DAY_LENGTH_TICKS);
        float clear = (float)smoothstep(0.0, ORBITAL_WARM_ELEVATION_SINE, elevation);
        float lit = (float)smoothstep(-0.05, ORBITAL_WARM_ELEVATION_SINE, elevation);
        SunVisual colour = interpolate(DAWN, NOON, clear);
        return new SunVisual(lerp(DAWN.brightness, NOON.brightness, lit),
                colour.red, colour.green, colour.blue);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static SunVisual interpolate(SunVisual start, SunVisual end, float progress) {
        float t = progress * progress * (3.0F - 2.0F * progress);
        return new SunVisual(
                lerp(start.brightness, end.brightness, t),
                lerp(start.red, end.red, t),
                lerp(start.green, end.green, t),
                lerp(start.blue, end.blue, t));
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static double wrapDayTime(double timeOfDay) {
        double wrapped = timeOfDay % DAY_LENGTH_TICKS;
        return wrapped < 0.0 ? wrapped + DAY_LENGTH_TICKS : wrapped;
    }

    private static final SunVisual DAWN = new SunVisual(0.35F, 1.00F, 0.58F, 0.30F);
    private static final SunVisual NOON = new SunVisual(1.00F, 1.00F, 1.00F, 0.94F);
    private static final SunVisual DUSK = new SunVisual(0.35F, 1.00F, 0.52F, 0.26F);
    private static final SunVisual MIDNIGHT = new SunVisual(0.04F, 0.38F, 0.52F, 1.00F);

    public record SunVisual(float brightness, float red, float green, float blue) {
    }
}
