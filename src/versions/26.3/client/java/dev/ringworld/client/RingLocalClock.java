package dev.ringworld.client;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingOrbitalDay;
import dev.ringworld.world.RingSkyProfile;
import net.minecraft.client.Minecraft;

/**
 * Client-side local time for the orbital day cycle (experiment branch, 2026-11-22).
 *
 * <p>Why here: in 26.3 every time-of-day effect on the client (sky, fog and cloud colour, star
 * angle and brightness, the vanilla sun angle, the lightmap's sky factor, RingWorld's own sun
 * tint) is sampled from the overworld {@code ClientClockInstance}. Reporting
 * {@link RingOrbitalDay#localTicks} for the camera's longitude from that one instance makes
 * all of them follow the player's local time at once, with no per-effect hooks. The clock's
 * own bookkeeping ({@code ClientClockManager.tick} / {@code handleUpdates}) reads and writes
 * the raw field, never {@code totalTicks()}, so the offset cannot accumulate.
 *
 * <p>Only the client clock is touched; the server keeps the global world clock for gameplay
 * (phase 2 adds position-dependent gameplay separately).
 */
public final class RingLocalClock {
    private RingLocalClock() { }

    /** Implemented by the client clock instance mixin. */
    public interface Marked {
        void ringworld$markOverworld();
        boolean ringworld$isOverworld();
        long ringworld$rawTotalTicks();
    }

    /** True when the RingWorld Overworld is active on this client with the orbital day cycle. */
    public static boolean orbitalActive() {
        return ClientRingState.geometry() != null
                && ClientRingState.skyProfile().dayCycle() == RingSkyProfile.DayCycle.ORBITAL;
    }

    /**
     * Writes the orbital-day shader inputs into column 2 of a texture matrix: x = WORLD day
     * fraction (undoing the camera's local offset), y = 1 when active, z = cos(tilt). Shaders
     * subtract each fragment's own longitude share to get its local sun.
     */
    public static org.joml.Matrix4f orbitalShaderParams(net.minecraft.client.multiplayer.ClientLevel world,
                                                        org.joml.Matrix4f matrix) {
        if (!orbitalActive() || world == null) return matrix.m20(0.0F).m21(0.0F).m22(0.0F);
        Minecraft client = Minecraft.getInstance();
        net.minecraft.client.Camera camera = client.gameRenderer.mainCamera();
        long offset = RingOrbitalDay.longitudeOffsetTicks(ClientRingState.geometry(), camera.position().x);
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double world_ = Math.floorMod(world.getOverworldClockTime() + offset, RingOrbitalDay.DAY_TICKS) + partial;
        return matrix.m20((float)(world_ / RingOrbitalDay.DAY_TICKS)).m21(1.0F)
                .m22((float)Math.cos(RingOrbitalDay.TILT_RADIANS));
    }

    /** The world clock as reported to this client's visuals: local time at the camera. */
    public static long adjust(long rawTicks) {
        if (!orbitalActive()) return rawTicks;
        Minecraft client = Minecraft.getInstance();
        if (client == null || !client.isSameThread()) return rawTicks;
        RingGeometry geometry = ClientRingState.geometry();
        net.minecraft.client.Camera camera = client.gameRenderer == null ? null : client.gameRenderer.mainCamera();
        double x = client.player == null ? 0.0 : camera != null ? camera.position().x : client.player.getX();
        return RingOrbitalDay.localTicks(geometry, rawTicks, x);
    }
}
