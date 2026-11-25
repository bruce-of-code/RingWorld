package dev.ringworld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Locator bar across the seam, server half (azimuth): shortest way round, not flat X. */
@Mixin(targets = "net.minecraft.world.waypoints.WaypointTransmitter$EntityAzimuthConnection")
abstract class LocatorAzimuthConnectionMixin {
    @Shadow @Final private ServerPlayer receiver;

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 ringworld$ringAzimuthDelta(Vec3 receiverPos, Vec3 sourcePos, Operation<Vec3> original) {
        Vec3 delta = original.call(receiverPos, sourcePos);
        RingGeometry geometry = ringworld$geometry(receiver.level());
        if (geometry == null) return delta;
        return new Vec3(geometry.shortestCircumferenceDelta(sourcePos.x, receiverPos.x), delta.y, delta.z);
    }

    private static RingGeometry ringworld$geometry(Level level) {
        return level instanceof ServerLevel server && level.dimension() == Level.OVERWORLD
                ? RingWorldServer.geometryFor(server) : null;
    }
}
