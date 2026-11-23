package dev.ringworld.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Locator bar across the seam: block- and chunk-precision waypoints carry canonical X, so a
 * player a few blocks past the seam read as ~a whole circumference away and the dot pointed
 * the long way round (and the up/down arrow projected a point on the far side of the ring).
 * Use the waypoint image nearest the camera for both yaw and pitch.
 */
@Mixin(targets = {
        "net.minecraft.world.waypoints.TrackedWaypoint$Vec3iWaypoint",
        "net.minecraft.world.waypoints.TrackedWaypoint$ChunkWaypoint"})
abstract class LocatorWaypointImageMixin {
    @ModifyReturnValue(method = "position", at = @At("RETURN"))
    private Vec3 ringworld$nearestImage(Vec3 position) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || position == null) return position;
        Minecraft client = Minecraft.getInstance();
        Camera camera = client.gameRenderer == null ? null : client.gameRenderer.mainCamera();
        double referenceX = camera != null ? camera.position().x
                : client.player != null ? client.player.getX() : Double.NaN;
        if (!Double.isFinite(referenceX)) return position;
        double x = geometry.nearestImageX(position.x, referenceX);
        return x == position.x ? position : new Vec3(x, position.y, position.z);
    }
}
