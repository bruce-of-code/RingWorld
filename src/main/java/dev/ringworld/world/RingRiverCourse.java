package dev.ringworld.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * Terrain-aware correction to the continuous river's centreline (experiment branch,
 * 2026-11-22).
 *
 * <p>Why: the analytic centreline ({@link RingMacroTerrain#analyticRiverCenterZ}) ignores the
 * terrain, so the river cut straight canyons through mountains it could have skirted and
 * carved land beside oceans it could have followed. This class samples the generator's
 * height WITHOUT the river once per world (the river's own carve is never fed back), then
 * picks, for every {@value #STEP_BLOCKS}-block step around the ring, a sideways offset from
 * the analytic line. It is a pressure, not a pathfinder: a Viterbi pass charges for terrain
 * above sea level under the channel's footprint, rewards wide existing water
 * ({@link #WIDE_WATER_BONUS}), and charges a little for distance from the analytic line (so
 * the meander survives on flat land) and for changing offset between steps (so the course
 * stays smooth and only detours when the terrain makes it worth it). Two laps around the
 * ring plus a light circular blur make the result seam-periodic.
 *
 * <p>Second revision (same day, after an in-game report of the course still crossing land
 * beside large water and cutting mountains): the candidate window now spans the whole usable
 * band instead of +/-64 blocks around the analytic line, and each candidate is scored over
 * the channel footprint (+/-{@value #FOOTPRINT_HALF_BLOCKS} blocks across, two samples per
 * step along) instead of one centre point, so a ridge beside the centre line or between two
 * steps is no longer free to cut. The build log reports, for the analytic line and for the
 * chosen course, how many steps cross high ground and how many stay on land while wide water
 * was available in the band.
 *
 * <p>Ownership and timing: courses live in a process-wide map keyed by (geometry, seed,
 * settings), so every {@link RingMacroTerrain} for the same world (router, biome stage, water
 * fill) sees the same course. {@link #ensure} is called from generator hooks that run BEFORE
 * any NoiseChunk exists on the calling thread (biome stage, terrain fill, height-column
 * query); building there cannot nest inside RingWorld's per-thread noise sampling context.
 * While a build is running, {@link #building()} is true on the builder threads and
 * {@link RingMacroTerrain#riverInfluence} returns 0, so the sampled heights are the
 * river-free terrain and the build never recurses. Other threads block in {@link #ensure}
 * until the course exists. Without a registered course (tests, tools without a generator)
 * the analytic line is used unchanged.
 */
public final class RingRiverCourse {
    /** Height of one terrain column at intrinsic (x, z), river excluded. */
    @FunctionalInterface
    public interface HeightSampler { int height(int x, int z); }

    static final int STEP_BLOCKS = 32;
    /** Candidate sideways offsets from the analytic line are this far apart, in blocks. */
    private static final int OFFSET_STEP = 8;
    /** Half-width of the scored channel footprint (the carve reaches ~+/-30 blocks). */
    private static final int FOOTPRINT_HALF_BLOCKS = 16;
    /** Terrain cost = mean + this x peak of (floor - seaLevel) over the footprint. */
    private static final double PEAK_WEIGHT = 0.5;
    /**
     * Reward (negative cost) per step for running through wide existing water, scaled by the
     * share of the footprint that is wide water. Lets the river follow oceans, big lakes and
     * wide vanilla rivers instead of carving land beside them, and link them up (owner
     * request 2026-11-22). Narrow water costs 0, like land at sea level.
     */
    private static final double WIDE_WATER_BONUS = 8.0;
    /** "Wide" = a run of underwater samples at least this many blocks across (2 chunks). */
    private static final int MIN_WIDE_WATER_BLOCKS = 32;
    private static final double DISTANCE_COST_PER_BLOCK = 0.02;
    private static final double TURN_COST_PER_BLOCK = 0.10;
    /** Largest offset change between neighbouring steps (slope 0.5). */
    private static final int MAX_TURN = 24;
    /** Circular blur after the path is chosen, in steps. */
    private static final double BLUR_SIGMA_STEPS = 1.5;
    /** Diagnostics: a step "crosses high ground" when its centre floor is this far above sea. */
    private static final int HIGH_GROUND_BLOCKS = 12;

    private record Key(RingGeometry geometry, long seed, RingWorldGenerationSettings settings) { }

    private static final Map<Key, RingRiverCourse> COURSES = new ConcurrentHashMap<>();
    private static final Object BUILD_LOCK = new Object();
    private static final ThreadLocal<Boolean> BUILDING = ThreadLocal.withInitial(() -> false);

    private final double circumference;
    private final double limit;
    private final double[] offsets;
    private final String diagnostics;

    private RingRiverCourse(double circumference, double limit, double[] offsets, String diagnostics) {
        this.circumference = circumference;
        this.limit = limit;
        this.offsets = offsets;
        this.diagnostics = diagnostics;
    }

    public static boolean building() {
        return BUILDING.get();
    }

    static RingRiverCourse lookup(RingGeometry geometry, long seed, RingWorldGenerationSettings settings) {
        return COURSES.get(new Key(geometry, seed, settings));
    }

    /**
     * Builds the course for this world on first call; later calls return at once.
     *
     * @param rimThicknessBlocks the wall thickness, so the channel keeps clear of the rims
     */
    public static void ensure(RingGeometry geometry, long seed, RingWorldGenerationSettings settings,
                              int seaLevel, int rimThicknessBlocks, HeightSampler sampler) {
        if (geometry == null || !settings.continuousRiver() || BUILDING.get()) return;
        Key key = new Key(geometry, seed, settings);
        if (COURSES.containsKey(key)) return;
        synchronized (BUILD_LOCK) {
            if (COURSES.containsKey(key)) return;
            long started = System.nanoTime();
            RingRiverCourse course = build(new RingMacroTerrain(geometry, seed, settings),
                    geometry, seaLevel, rimThicknessBlocks, sampler);
            COURSES.put(key, course);
            dev.ringworld.RingWorldMod.LOGGER.info("Built terrain-aware river course in {} ms: {}",
                    (System.nanoTime() - started) / 1_000_000, course.diagnostics);
        }
    }

    /** Analytic centre {@code z0} at {@code blockX}, nudged toward low ground and kept in band. */
    double adjust(double blockX, double z0) {
        double position = blockX / circumference * offsets.length;
        int n = offsets.length;
        int i1 = Math.floorMod((int)Math.floor(position), n);
        double t = position - Math.floor(position);
        double p0 = offsets[Math.floorMod(i1 - 1, n)], p1 = offsets[i1];
        double p2 = offsets[(i1 + 1) % n], p3 = offsets[(i1 + 2) % n];
        // Periodic Catmull-Rom: smooth through the samples, no kinks every step.
        double offset = 0.5 * (2.0 * p1 + (-p0 + p2) * t
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t * t
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t * t * t);
        return Math.max(-limit, Math.min(limit, z0 + offset));
    }

    private static RingRiverCourse build(RingMacroTerrain macro, RingGeometry geometry, int seaLevel,
                                         int rimThicknessBlocks, HeightSampler sampler) {
        double circumference = geometry.circumferenceBlocks();
        int steps = Math.max(16, (int)Math.round(circumference / STEP_BLOCKS));
        double maxWidth = Math.max(6.0, Math.min(15.0, geometry.widthBlocks() * 0.055)) * 1.08;
        double corridorHalf = maxWidth + Math.max(5.0, maxWidth * 0.65);
        double room = Math.max(4.0, geometry.widthBlocks() * 0.21);
        double limit = Math.max(room, geometry.widthBlocks() / 2.0 - rimThicknessBlocks - corridorHalf - 4.0);
        // Offsets from the analytic line wide enough to reach either edge of the usable band
        // from anywhere the analytic line can be.
        int maxOffset = (int)Math.ceil(2.0 * limit / OFFSET_STEP) * OFFSET_STEP;
        int states = 2 * maxOffset / OFFSET_STEP + 1;
        int zero = maxOffset / OFFSET_STEP;
        int footprint = FOOTPRINT_HALF_BLOCKS / OFFSET_STEP;

        double[][] cost = new double[steps][states];
        int[][] centreFloor = new int[steps][states];
        boolean[][] centreWide = new boolean[steps][states];
        boolean[] wideAvailable = new boolean[steps];
        IntStream.range(0, steps).parallel().forEach(i -> {
            boolean previous = BUILDING.get();
            BUILDING.set(true);
            try {
                double x0 = i * circumference / steps;
                double z0 = macro.analyticRiverCenterZ(x0);
                boolean[] inBand = new boolean[states];
                for (int s = 0; s < states; s++) inBand[s] = Math.abs(z0 + s * OFFSET_STEP - maxOffset) <= limit;
                // Two rows along the step: at the step and halfway to the next one.
                int[][] floor = new int[2][states];
                boolean[][] wide = new boolean[2][states];
                for (int row = 0; row < 2; row++) {
                    int x = (int)Math.round(x0 + row * STEP_BLOCKS / 2.0);
                    double zr = macro.analyticRiverCenterZ(x);
                    for (int s = 0; s < states; s++) {
                        if (inBand[s]) floor[row][s] = sampler.height(x, (int)Math.round(zr + s * OFFSET_STEP - maxOffset));
                    }
                    markWideWater(floor[row], inBand, seaLevel, wide[row]);
                }
                for (int s = 0; s < states; s++) {
                    centreFloor[i][s] = floor[0][s];
                    centreWide[i][s] = inBand[s] && wide[0][s];
                    wideAvailable[i] |= centreWide[i][s];
                    if (!inBand[s]) { cost[i][s] = Double.POSITIVE_INFINITY; continue; }
                    double sum = 0.0, peak = 0.0;
                    int samples = 0, wideSamples = 0;
                    for (int row = 0; row < 2; row++) {
                        for (int f = s - footprint; f <= s + footprint; f++) {
                            if (f < 0 || f >= states || !inBand[f]) continue;
                            double above = Math.max(0, floor[row][f] - seaLevel);
                            sum += above;
                            peak = Math.max(peak, above);
                            samples++;
                            if (wide[row][f]) wideSamples++;
                        }
                    }
                    double terrain = sum / samples + PEAK_WEIGHT * peak
                            - WIDE_WATER_BONUS * wideSamples / (double)samples;
                    cost[i][s] = terrain + DISTANCE_COST_PER_BLOCK * Math.abs(s * OFFSET_STEP - maxOffset);
                }
            } finally {
                BUILDING.set(previous);
            }
        });

        // Viterbi over two laps; the second lap's choice no longer depends on the start.
        int laps = 2 * steps;
        double[] best = new double[states];
        int[][] from = new int[laps][states];
        for (int s = 0; s < states; s++) best[s] = cost[0][s];
        int maxTurnStates = MAX_TURN / OFFSET_STEP;
        for (int k = 1; k < laps; k++) {
            double[] next = new double[states];
            for (int s = 0; s < states; s++) {
                double bestValue = Double.POSITIVE_INFINITY;
                int bestFrom = s;
                for (int d = -maxTurnStates; d <= maxTurnStates; d++) {
                    int p = s + d;
                    if (p < 0 || p >= states) continue;
                    double value = best[p] + TURN_COST_PER_BLOCK * Math.abs(d) * OFFSET_STEP;
                    if (value < bestValue) { bestValue = value; bestFrom = p; }
                }
                next[s] = bestValue + cost[k % steps][s];
                from[k][s] = bestFrom;
            }
            best = next;
        }
        int state = zero;
        for (int s = 0; s < states; s++) if (best[s] < best[state]) state = s;
        int[] chosen = new int[steps];
        for (int k = laps - 1; k >= steps; k--) {
            chosen[k - steps] = state;
            state = from[k][state];
        }
        double[] raw = new double[steps];
        boolean anyFinite = false;
        for (int i = 0; i < steps; i++) {
            raw[i] = chosen[i] * OFFSET_STEP - maxOffset;
            anyFinite |= cost[i][chosen[i]] != Double.POSITIVE_INFINITY;
        }
        if (!anyFinite) java.util.Arrays.fill(raw, 0.0);

        // Light circular blur: smooths the 8-block quantisation and closes the seam.
        int radius = (int)Math.ceil(BLUR_SIGMA_STEPS * 3.0);
        double[] offsets = new double[steps];
        for (int i = 0; i < steps; i++) {
            double sum = 0.0, weight = 0.0;
            for (int d = -radius; d <= radius; d++) {
                double w = Math.exp(-0.5 * d * d / (BLUR_SIGMA_STEPS * BLUR_SIGMA_STEPS));
                sum += raw[Math.floorMod(i + d, steps)] * w;
                weight += w;
            }
            offsets[i] = sum / weight;
        }

        String diagnostics = String.format(java.util.Locale.ROOT,
                "%d steps, band +/-%.0f; analytic line: %s; course: %s",
                steps, limit,
                describe(centreFloor, centreWide, wideAvailable, i -> zero, seaLevel),
                describe(centreFloor, centreWide, wideAvailable, i -> chosen[i], seaLevel));
        return new RingRiverCourse(circumference, limit, offsets, diagnostics);
    }

    /** Marks runs of underwater samples (floor below the sea surface) at least 2 chunks wide. */
    private static void markWideWater(int[] floor, boolean[] inBand, int seaLevel, boolean[] wide) {
        for (int s = 0; s < floor.length; ) {
            if (!inBand[s] || floor[s] >= seaLevel) { s++; continue; }
            int end = s;
            while (end + 1 < floor.length && inBand[end + 1] && floor[end + 1] < seaLevel) end++;
            if ((end - s + 1) * OFFSET_STEP >= MIN_WIDE_WATER_BLOCKS) {
                for (int k = s; k <= end; k++) wide[k] = true;
            }
            s = end + 1;
        }
    }

    private static String describe(int[][] floor, boolean[][] wide, boolean[] wideAvailable,
                                   java.util.function.IntUnaryOperator path, int seaLevel) {
        int high = 0, inWide = 0, landBesideWide = 0;
        for (int i = 0; i < floor.length; i++) {
            int s = path.applyAsInt(i);
            if (floor[i][s] - seaLevel >= HIGH_GROUND_BLOCKS) high++;
            if (wide[i][s]) inWide++;
            else if (wideAvailable[i] && floor[i][s] >= seaLevel) landBesideWide++;
        }
        int unavoidableHigh = 0;
        for (int[] row : floor) {
            int lowest = Integer.MAX_VALUE;
            for (int v : row) if (v != 0) lowest = Math.min(lowest, v);
            if (lowest != Integer.MAX_VALUE && lowest - seaLevel >= HIGH_GROUND_BLOCKS) unavoidableHigh++;
        }
        return String.format(java.util.Locale.ROOT,
                "%d high-ground (%d with the whole band high), %d in wide water, %d on land while wide water was in band",
                high, unavoidableHigh, inWide, landBesideWide);
    }
}
