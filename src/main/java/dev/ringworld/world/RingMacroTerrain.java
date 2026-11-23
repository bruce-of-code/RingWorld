package dev.ringworld.world;

/** Fast seed-derived macro terrain shared by chunk generation, previews, and tests. */
public final class RingMacroTerrain {
    private final RingGeometry geometry;
    private final long seed;
    private final RingWorldGenerationSettings settings;

    public RingMacroTerrain(RingGeometry geometry, long seed, RingWorldGenerationSettings settings) {
        this.geometry = geometry;
        this.seed = seed;
        this.settings = settings;
    }

    public boolean active() {
        return settings.layout() == RingWorldLayout.ARCHIPELAGO || settings.continuousRiver();
    }

    /** -1 is deep water preference; +1 is island interior. Seam-periodic by construction. */
    public double landBias(double blockX, double blockZ) {
        if (settings.layout() != RingWorldLayout.ARCHIPELAGO) return 0.0;
        double x = geometry.wrapX(blockX);
        double best = islandScore(x, blockZ, 0.0, 0.0,
                Math.min(220.0, geometry.circumferenceBlocks() * 0.08),
                Math.min(68.0, geometry.widthBlocks() * 0.32));
        double cellSizeX = Math.max(192.0, Math.min(448.0, geometry.circumferenceBlocks() / 12.0));
        double cellSizeZ = Math.max(96.0, Math.min(192.0, geometry.widthBlocks() * 0.62));
        int cellCountX = Math.max(1, (int)Math.round(geometry.circumferenceBlocks() / cellSizeX));
        int baseX = Math.min(cellCountX - 1,
                (int)(x * cellCountX / geometry.circumferenceBlocks()));
        int baseZ = (int)Math.floor((blockZ - geometry.minWidthZ()) / cellSizeZ);
        for (int dx = -1; dx <= 1; dx++) {
            int cellX = Math.floorMod(baseX + dx, cellCountX);
            for (int dz = -1; dz <= 1; dz++) {
                int cellZ = baseZ + dz;
                long hash = mix(seed ^ (long)cellX * 0x9E3779B97F4A7C15L
                        ^ (long)cellZ * 0xD1B54A32D192ED03L);
                double centerX = (cellX + unit(hash)) * geometry.circumferenceBlocks() / cellCountX;
                double centerZ = geometry.minWidthZ() + (cellZ + unit(hash >>> 21)) * cellSizeZ;
                double radiusX = 74.0 + unit(hash >>> 42) * 150.0;
                double radiusZ = 24.0 + unit(mix(hash)) * Math.min(60.0, geometry.widthBlocks() * 0.23);
                best = Math.max(best, islandScore(x, blockZ, centerX, centerZ, radiusX, radiusZ));
            }
        }
        if (best <= 0.0) return -0.82;
        double smooth = best * best * (3.0 - 2.0 * best);
        return -0.82 + smooth * 1.82;
    }

    /*
     * River shape (experiment branch, 2026-11-22). The original centreline used only
     * whole-ring harmonics 1..13, so on the default 16384-block ring its shortest wave was
     * ~1260 blocks long and the width changed over ~2340 blocks: anywhere a player looks the
     * river read as a straight, fixed-width channel. Local meander and width terms are now
     * specified by WAVELENGTH IN BLOCKS and converted to the nearest whole ring harmonic
     * k = round(C / wavelength), so every term is still exactly periodic at the X seam
     * (sin(2*pi*k*x/C) with integer k) on any ring size. The total offset still stays within
     * the original `room`, so the channel keeps the same clearance from the rims.
     */
    /** Share of `room` given to the ring-scale sweep; the rest goes to the local meander. */
    private static final double RIVER_SWEEP_SHARE = 0.55;
    /** Local meander wavelengths (blocks) and relative weights; each wavelength is jittered
     *  +/-20 % per seed so different worlds don't share one rhythm. */
    private static final double[] RIVER_MEANDER_WAVELENGTHS = {960.0, 430.0, 190.0};
    private static final double[] RIVER_MEANDER_WEIGHTS = {1.0, 0.55, 0.25};
    /** Width variation: two waves spanning 0.60..1.08 x base. Narrowest is ~2/3 of the old
     *  typical 0.9 x base; widest unchanged, so rim clearance is unchanged. Wavelengths halved
     *  from 720/260 so the width changes within a player's view (owner request 2026-11-22). */
    private static final double[] RIVER_WIDTH_WAVELENGTHS = {360.0, 130.0};
    private static final double[] RIVER_WIDTH_WEIGHTS = {0.6, 0.4};

    /** Resolved lazily: the generator registers the course on first use (server only). */
    private volatile RingRiverCourse riverCourse;

    /**
     * River centreline: the analytic line, nudged toward low terrain by the world's
     * {@link RingRiverCourse} when one is registered (experiment, 2026-11-22).
     */
    public double riverCenterZ(double blockX) {
        double z0 = analyticRiverCenterZ(blockX);
        RingRiverCourse course = riverCourse;
        if (course == null && !RingRiverCourse.building()) {
            course = RingRiverCourse.lookup(geometry, seed, settings);
            if (course != null) riverCourse = course;
        }
        return course == null ? z0 : course.adjust(geometry.wrapX(blockX), z0);
    }

    /** Smooth, non-repeating-looking centreline: ring-scale sweep plus a local meander. */
    double analyticRiverCenterZ(double blockX) {
        double angle = Math.PI * 2.0 * geometry.wrapX(blockX) / geometry.circumferenceBlocks();
        double room = Math.max(4.0, geometry.widthBlocks() * 0.21);
        double value = 0.0;
        double weight = 0.0;
        for (int harmonic : new int[]{1, 2, 3, 5, 8, 13}) {
            long hash = mix(seed ^ 0x52495645524C4F4FL ^ harmonic * 0x9E3779B97F4A7C15L);
            double amplitude = (0.55 + unit(hash) * 0.45) / Math.pow(harmonic, 0.78);
            double phase = unit(hash >>> 27) * Math.PI * 2.0;
            value += Math.sin(angle * harmonic + phase) * amplitude;
            weight += amplitude;
        }
        double meander = periodicWaves(angle, 0x4D45414E4445524CL,
                RIVER_MEANDER_WAVELENGTHS, RIVER_MEANDER_WEIGHTS);
        return (RIVER_SWEEP_SHARE * value / weight + (1.0 - RIVER_SWEEP_SHARE) * meander) * room;
    }

    public double riverWidth(double blockX) {
        double angle = Math.PI * 2.0 * geometry.wrapX(blockX) / geometry.circumferenceBlocks();
        double base = Math.max(6.0, Math.min(15.0, geometry.widthBlocks() * 0.055));
        double variation = periodicWaves(angle, 0x57494454484C4F4FL,
                RIVER_WIDTH_WAVELENGTHS, RIVER_WIDTH_WEIGHTS);
        return base * (0.84 + 0.24 * variation);
    }

    /**
     * Weighted sum of sines in [-1, 1]. Each wavelength (blocks, jittered +/-20 % by seed)
     * becomes the nearest integer ring harmonic, so the result is seam-periodic.
     */
    private double periodicWaves(double angle, long salt, double[] wavelengths, double[] weights) {
        double circumference = geometry.circumferenceBlocks();
        double value = 0.0;
        double weight = 0.0;
        for (int i = 0; i < wavelengths.length; i++) {
            long hash = mix(seed ^ salt ^ (i + 1) * 0xD1B54A32D192ED03L);
            double wavelength = wavelengths[i] * (0.8 + 0.4 * unit(hash));
            int harmonic = Math.max(1, (int)Math.round(circumference / wavelength));
            double phase = unit(hash >>> 27) * Math.PI * 2.0;
            value += Math.sin(angle * harmonic + phase) * weights[i];
            weight += weights[i];
        }
        return value / weight;
    }

    /**
     * Lowest Y the river's density carve clears at this influence: seven below sea level in
     * the core, rising through the banks. Single source for the noise routers' FINAL_DENSITY
     * carve and {@link RingRiverWater}, which must agree on which cells form the channel.
     */
    public static double riverChannelFloor(double river, int seaLevel) {
        return seaLevel - 7.0 + (1.0 - river) * 9.0;
    }

    /** 0 outside the banks, 1 in the channel core. */
    public double riverInfluence(double blockX, double blockZ) {
        // While the course is being built its height samples must see river-free terrain.
        if (!settings.continuousRiver() || RingRiverCourse.building()) return 0.0;
        double distance = Math.abs(blockZ - riverCenterZ(blockX));
        double width = riverWidth(blockX);
        double bank = Math.max(5.0, width * 0.65);
        if (distance >= width + bank) return 0.0;
        if (distance <= width) return 1.0;
        double t = 1.0 - (distance - width) / bank;
        return t * t * (3.0 - 2.0 * t);
    }

    private double islandScore(double x, double z, double centerX, double centerZ,
                               double radiusX, double radiusZ) {
        double dx = geometry.shortestCircumferenceDelta(centerX, x) / Math.max(1.0, radiusX);
        double dz = (z - centerZ) / Math.max(1.0, radiusZ);
        double distance = Math.sqrt(dx * dx + dz * dz);
        double edge = 0.06 * Math.sin(dx * 11.0 + dz * 7.0 + seed * 0.000001)
                + 0.035 * Math.sin(dx * 23.0 - dz * 17.0 + seed * 0.000003);
        return Math.max(0.0, Math.min(1.0, 1.0 - distance + edge));
    }

    private static double unit(long value) {
        return (value & 0x1F_FFFFL) / (double)0x20_0000L;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
