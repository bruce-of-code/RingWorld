package dev.ringworld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Locator bar across the seam, server half (precision). Vanilla drops to azimuth-only past
 * 332 blocks of straight-line distance; across the seam that distance is ~a circumference
 * even for a neighbour. Judge it by the shortest way round the ring.
 */
@Mixin(LivingEntity.class)
abstract class LocatorWaypointServerMixin {
    @WrapOperation(method = "makeWaypointConnectionWith", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/waypoints/WaypointTransmitter;isReallyFar(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;)Z"))
    private boolean ringworld$reallyFarRoundTheRing(LivingEntity source, ServerPlayer receiver, Operation<Boolean> original) {
        RingGeometry geometry = ringworld$geometry(source.level());
        if (geometry == null) return original.call(source, receiver);
        double dx = geometry.shortestCircumferenceDelta(source.getX(), receiver.getX());
        double dy = receiver.getY() - source.getY();
        double dz = receiver.getZ() - source.getZ();
        return dx * dx + dy * dy + dz * dz > 332.0 * 332.0;
    }

    private static RingGeometry ringworld$geometry(Level level) {
        return level instanceof ServerLevel server && level.dimension() == Level.OVERWORLD
                ? RingWorldServer.geometryFor(server) : null;
    }
}
