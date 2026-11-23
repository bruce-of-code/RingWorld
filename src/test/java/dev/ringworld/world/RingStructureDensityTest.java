package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingStructureDensityTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(256, 16_384);

    @Test
    void candidateGridIsPeriodicAndFiniteWidth() {
        int count = 0;
        for (int x = 0; x < GEOMETRY.circumferenceChunks(); x++) {
            for (int z = GEOMETRY.minChunkZ(); z <= GEOMETRY.maxChunkZ(); z++) {
                boolean candidate = RingStructureDensity.isAdditionalCandidate(
                        42L, GEOMETRY, 10387312, 32, 8, x, z);
                assertEquals(candidate, RingStructureDensity.isAdditionalCandidate(
                        42L, GEOMETRY, 10387312, 32, 8,
                        x + GEOMETRY.circumferenceChunks(), z));
                if (candidate) count++;
            }
        }
        assertTrue(count > 0);
        assertFalse(RingStructureDensity.isAdditionalCandidate(
                42L, GEOMETRY, 10387312, 32, 8, 0, GEOMETRY.maxChunkZ() + 4));
    }

    /** Experiment 2026-11-22: 1.5x and 2x hit their totals for shipwreck (24/4) and
     *  pillager outpost (32/8) spacing on the default band. */
    @Test
    void abundanceLevelsHitTheirTargets() {
        // Outposts get only ~5 extra candidates per ring at 1.5x; 64 seeds keeps sampling
        // noise well inside the 15 % tolerance.
        final int SEEDS = 64;
        int[][] spacings = {{24, 4}, {32, 8}};
        for (int[] s : spacings) {
            // Vanilla's expected count on the band: every random-spread cell, every offset in
            // [0, spacing - separation), counting those that land on a band row.
            double vanillaPerColumn = 0.0;
            int range = s[0] - s[1];
            for (int cellZ = -4; cellZ <= 4; cellZ++) {
                for (int offset = 0; offset < range; offset++) {
                    int z = cellZ * s[0] + offset;
                    if (z >= GEOMETRY.minChunkZ() && z <= GEOMETRY.maxChunkZ()) vanillaPerColumn += 1.0 / range;
                }
            }
            double vanilla = (double)GEOMETRY.circumferenceChunks() / s[0] * vanillaPerColumn;
            for (RingStructureAbundance level : new RingStructureAbundance[]{
                    RingStructureAbundance.ONE_AND_HALF, RingStructureAbundance.DOUBLE}) {
                int extra = 0;
                for (long seed = 1; seed <= SEEDS; seed++) {
                    for (int x = 0; x < GEOMETRY.circumferenceChunks(); x++) {
                        for (int z = GEOMETRY.minChunkZ(); z <= GEOMETRY.maxChunkZ(); z++) {
                            if (RingStructureDensity.isAdditionalCandidate(seed, GEOMETRY, 77, s[0], s[1],
                                    x, z, level.extraFraction())) extra++;
                        }
                    }
                }
                double ratio = extra / (double)SEEDS / vanilla;
                assertEquals(level.extraFraction(), ratio, level.extraFraction() * 0.15,
                        level + " spacing " + s[0] + ": extra/vanilla=" + ratio);
            }
        }
    }

    @Test
    void legacyMoreStructuresReadsAsDouble() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("atlas_fidelity", RingAtlasFidelity.BALANCED.id());
        json.addProperty("layout", RingWorldLayout.VANILLA.id());
        json.addProperty("continuous_river", false);
        json.addProperty("more_structures", true);
        json.addProperty("format", RingWorldGenerationSettings.FORMAT_VERSION);
        RingWorldGenerationSettings legacy = RingWorldGenerationSettings.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(RingStructureAbundance.DOUBLE, legacy.structureAbundance());

        RingWorldGenerationSettings half = RingWorldGenerationSettings.DEFAULT
                .withStructureAbundance(RingStructureAbundance.ONE_AND_HALF);
        var encoded = RingWorldGenerationSettings.CODEC
                .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, half).getOrThrow();
        assertEquals(half, RingWorldGenerationSettings.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void differentSeedsMoveCandidates() {
        boolean differs = false;
        for (int x = 0; x < GEOMETRY.circumferenceChunks() && !differs; x++) {
            for (int z = GEOMETRY.minChunkZ(); z <= GEOMETRY.maxChunkZ(); z++) {
                differs |= RingStructureDensity.isAdditionalCandidate(1, GEOMETRY, 2, 24, 6, x, z)
                        != RingStructureDensity.isAdditionalCandidate(3, GEOMETRY, 2, 24, 6, x, z);
            }
        }
        assertTrue(differs);
    }
}
