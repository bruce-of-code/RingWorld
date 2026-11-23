package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingMacroTerrainTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(256, 16_384);

    @Test
    void riverIsContinuousAtTheCanonicalSeam() {
        RingWorldGenerationSettings settings = RingWorldGenerationSettings.DEFAULT
                .withContinuousRiver(true);
        RingMacroTerrain field = new RingMacroTerrain(GEOMETRY, 991L, settings);
        assertEquals(field.riverCenterZ(0.0), field.riverCenterZ(16_384.0), 1.0e-9);
        assertEquals(field.riverWidth(-0.25), field.riverWidth(16_383.75), 1.0e-9);
        assertTrue(field.riverInfluence(7_000, field.riverCenterZ(7_000)) > 0.99);
    }

    /** Experiment 2026-11-22: the river must visibly wander and vary in width within a
     *  player-scale stretch, while staying inside its original clearance from the rims. */
    @Test
    void riverMeandersLocallyAndStaysInsideItsRoom() {
        RingWorldGenerationSettings settings = RingWorldGenerationSettings.DEFAULT
                .withContinuousRiver(true);
        double room = GEOMETRY.widthBlocks() * 0.21;
        for (long seed : new long[]{1L, 991L, 123456789L}) {
            RingMacroTerrain field = new RingMacroTerrain(GEOMETRY, seed, settings);
            int windows = 0;
            int wandering = 0;
            for (int start = 0; start < GEOMETRY.circumferenceBlocks(); start += 1_000, windows++) {
                double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                double minW = Double.MAX_VALUE, maxW = -Double.MAX_VALUE;
                for (int x = start; x < start + 1_000; x += 4) {
                    double z = field.riverCenterZ(x);
                    double w = field.riverWidth(x);
                    assertTrue(Math.abs(z) <= room + 1.0e-9, "centre outside room at " + x);
                    minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    minW = Math.min(minW, w); maxW = Math.max(maxW, w);
                }
                if (maxZ - minZ >= 8.0 && maxW - minW >= 1.0) wandering++;
            }
            assertTrue(wandering * 4 >= windows * 3,
                    "seed " + seed + ": only " + wandering + "/" + windows + " 1000-block stretches wander");
        }
    }

    @Test
    void archipelagoIsPeriodicAndContainsWaterAndLand() {
        RingWorldGenerationSettings settings = RingWorldGenerationSettings.DEFAULT
                .withLayout(RingWorldLayout.ARCHIPELAGO);
        RingMacroTerrain field = new RingMacroTerrain(GEOMETRY, 12345L, settings);
        assertEquals(field.landBias(31.25, 17.0), field.landBias(16_415.25, 17.0), 1.0e-9);
        boolean land = false;
        boolean water = false;
        for (int x = 0; x < GEOMETRY.circumferenceBlocks(); x += 128) {
            for (int z = GEOMETRY.minWidthZ(); z <= GEOMETRY.maxWidthZ(); z += 32) {
                land |= field.landBias(x, z) > 0.1;
                water |= field.landBias(x, z) < -0.5;
            }
        }
        assertTrue(land, "layout must retain islands");
        assertTrue(water, "layout must retain ocean channels");
    }

    @Test
    void vanillaDefaultHasNoMacroBias() {
        RingMacroTerrain field = new RingMacroTerrain(GEOMETRY, 1L,
                RingWorldGenerationSettings.DEFAULT);
        assertFalse(field.active());
        assertEquals(0.0, field.landBias(100, 0));
        assertEquals(0.0, field.riverInfluence(100, 0));
    }
}
