package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingOrbitalDayTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(256, 16_384);

    /**
     * The local-time rule plus vanilla's sky convention must reproduce the physical picture:
     * a star fixed in space, tilted out of the ring plane, seen from a point on the inner
     * surface of a ring spinning once per day. Physical frame as in RingGeometry.toPhysical:
     * (Z, r cos a, r sin a); local up is toward the axis, local east is +X.
     */
    @Test
    void vanillaSkyAtLocalTimeMatchesPhysicalStar() {
        double tilt = RingOrbitalDay.TILT_RADIANS;
        for (long world = 0; world < 24_000; world += 731) {
            // Star direction: rotating in-plane angle psi, axial component sin(tilt).
            double psi = -Math.PI + 2.0 * Math.PI * (world - 6000.0) / 24_000.0;
            double[] star = {Math.sin(tilt), Math.cos(tilt) * Math.cos(psi), Math.cos(tilt) * Math.sin(psi)};
            for (double x = 0; x < GEOMETRY.circumferenceBlocks(); x += 977) {
                double a = 2.0 * Math.PI * x / GEOMETRY.circumferenceBlocks();
                double[] up = {0.0, -Math.cos(a), -Math.sin(a)};
                double[] east = {0.0, -Math.sin(a), Math.cos(a)};
                double physicalElevation = dot(star, up);
                double physicalEast = dot(star, east);

                // Vanilla: pose R_y(-90) R_x(theta) R_z(-tilt) applied to +Y, theta from local time.
                long local = RingOrbitalDay.localTicks(GEOMETRY, world, x);
                double theta = 2.0 * Math.PI * (local - 6000.0) / 24_000.0;
                double vanillaUp = Math.cos(theta) * Math.cos(tilt);
                double vanillaEast = -Math.sin(theta) * Math.cos(tilt);

                assertEquals(physicalElevation, vanillaUp, 2e-3, "elevation at x=" + x + " t=" + world);
                assertEquals(physicalEast, vanillaEast, 2e-3, "east at x=" + x + " t=" + world);
                assertEquals(physicalElevation, RingOrbitalDay.sunElevationSine(GEOMETRY, world, x), 2e-3);
            }
        }
    }

    @Test
    void halfTheRingIsLitAndTheSeamIsContinuous() {
        int lit = 0, samples = 0;
        for (double x = 0; x < GEOMETRY.circumferenceBlocks(); x += 16, samples++) {
            if (RingOrbitalDay.sunElevationSine(GEOMETRY, 3_000, x) > 0) lit++;
        }
        assertEquals(0.5, lit / (double)samples, 0.01);
        assertEquals(RingOrbitalDay.localTicks(GEOMETRY, 50_000, 0.0) % 24_000,
                RingOrbitalDay.localTicks(GEOMETRY, 50_000, GEOMETRY.circumferenceBlocks()) % 24_000);
        assertTrue(RingOrbitalDay.localTicks(GEOMETRY, 10, 8_000) >= 0);
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
