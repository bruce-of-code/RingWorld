package dev.ringworld.world;

/** Deterministic, periodic second candidate grid used by the opt-in structure-density setting. */
public final class RingStructureDensity {
    private RingStructureDensity() { }

    /** 2x vanilla in total; see {@link #isAdditionalCandidate(long, RingGeometry, int, int, int, int, int, double)}. */
    public static boolean isAdditionalCandidate(long seed, RingGeometry geometry, int structureSalt,
                                                 int vanillaSpacing, int separation,
                                                 int chunkX, int chunkZ) {
        return isAdditionalCandidate(seed, geometry, structureSalt, vanillaSpacing, separation,
                chunkX, chunkZ, RingStructureAbundance.DOUBLE.extraFraction());
    }

    /**
     * True if this chunk is an extra candidate for one structure set.
     *
     * <p>{@code extraFraction} is how many extra candidates to add as a fraction of vanilla's
     * own count over the band (0.5 = 1.5x total, 1.0 = 2x). The grid below has 0.7x vanilla
     * spacing (about 2.04x vanilla's candidate density) but discards extras within the rim
     * margin, so its raw yield relative to vanilla depends on band width: about 1.5x on the
     * default 16-chunk band, and less on narrow bands (experiment, 2026-11-22). Each grid
     * candidate is kept with probability {@code extraFraction / rawYield}, from its own seeded
     * hash, so the total lands on the requested multiple wherever the grid can supply it.
     * When it can't (very narrow bands), every candidate is kept.
     */
    public static boolean isAdditionalCandidate(long seed, RingGeometry geometry, int structureSalt,
                                                 int vanillaSpacing, int separation,
                                                 int chunkX, int chunkZ, double extraFraction) {
        if (extraFraction <= 0.0) return false;
        int circumferenceChunks = geometry.circumferenceChunks();
        int canonicalX = Math.floorMod(chunkX, circumferenceChunks);
        int spacing = Math.max(separation + 1, (int)Math.round(vanillaSpacing * 0.70));
        int cellsX = Math.max(1, (int)Math.round((double)circumferenceChunks / spacing));
        int cellX = Math.min(cellsX - 1, (int)((long)canonicalX * cellsX / circumferenceChunks));
        int startX = (int)((long)cellX * circumferenceChunks / cellsX);
        int endX = (int)((long)(cellX + 1) * circumferenceChunks / cellsX);
        int spanX = Math.max(1, endX - startX);

        int cellZ = Math.floorDiv(chunkZ, spacing);
        long hash = mix(seed ^ Integer.toUnsignedLong(structureSalt) * 0x9E3779B97F4A7C15L
                ^ (long)cellX * 0xD1B54A32D192ED03L ^ (long)cellZ * 0x94D049BB133111EBL);
        int margin = Math.min(separation, Math.max(0, Math.min(spanX, spacing) / 3));
        int xRange = Math.max(1, spanX - margin);
        int zRange = Math.max(1, spacing - margin);
        int candidateX = Math.floorMod(startX + margin / 2
                + (int)Math.floorMod(hash, xRange), circumferenceChunks);
        int candidateZ = cellZ * spacing + margin / 2
                + (int)Math.floorMod(hash >>> 32, zRange);

        int rimMargin = Math.min(3, Math.max(0, geometry.widthChunks() / 8));
        int minZ = Math.floorDiv(geometry.minWidthZ(), 16) + rimMargin;
        int maxZ = Math.floorDiv(geometry.maxWidthZ(), 16) - rimMargin;
        if (canonicalX != candidateX || chunkZ != candidateZ || chunkZ < minZ || chunkZ > maxZ) return false;

        // Raw yield vs vanilla, counted exactly per grid column rather than by area: neither grid
        // is aligned to the band, so the cells straddling its edges only partly count (an area
        // estimate overstated this grid's yield by ~20 % and undershot 1.5x as 1.41x). Vanilla's
        // random spread is modelled as uniform over [0, spacing - separation) in each cell.
        double extraPerRing = cellsX * expectedPerColumn(spacing, margin / 2, zRange, minZ, maxZ);
        double vanillaPerRing = (double)circumferenceChunks / vanillaSpacing * expectedPerColumn(
                vanillaSpacing, 0, Math.max(1, vanillaSpacing - separation),
                geometry.minChunkZ(), geometry.maxChunkZ());
        if (vanillaPerRing <= 0.0) return true;
        double rawYield = extraPerRing / vanillaPerRing;
        if (rawYield <= extraFraction) return true;
        double keep = extraFraction / rawYield;
        return (mix(hash ^ 0x4B454550L) >>> 11) * 0x1.0p-53 < keep;
    }

    /** Expected candidates in rows [minZ, maxZ] for one column of a grid whose cell at index c
     *  places its candidate uniformly in [c*spacing + offsetStart, + offsetRange). */
    static double expectedPerColumn(int spacing, int offsetStart, int offsetRange, int minZ, int maxZ) {
        if (maxZ < minZ) return 0.0;
        double total = 0.0;
        for (int cell = Math.floorDiv(minZ, spacing) - 1; cell <= Math.floorDiv(maxZ, spacing); cell++) {
            int low = cell * spacing + offsetStart;
            int high = low + offsetRange - 1;
            int from = Math.max(low, minZ), to = Math.min(high, maxZ);
            if (to >= from) total += (to - from + 1) / (double)offsetRange;
        }
        return total;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
