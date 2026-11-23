package dev.ringworld.mixin;

import dev.ringworld.server.RingServerOrbital;
import java.util.Map;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.SpatialAttributeInterpolator;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Orbital day: on the RingWorld Overworld's server attribute system, time-based attributes
 * are evaluated at the local time of the queried position (or, for position-free queries,
 * of whatever is being ticked). See {@link RingServerOrbital}.
 */
@Mixin(EnvironmentAttributeSystem.class)
abstract class OrbitalAttributeSystemMixin {
    @Shadow @Final private Map<EnvironmentAttribute<?>, ?> attributeSamplers;

    @Inject(method = "getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/attribute/SpatialAttributeInterpolator;)Ljava/lang/Object;",
            at = @At("HEAD"), cancellable = true)
    private void ringworld$localValue(EnvironmentAttribute<?> attribute, Vec3 position,
                                      SpatialAttributeInterpolator interpolator, CallbackInfoReturnable<Object> cir) {
        if (position == null || !RingServerOrbital.ownsSystem(this)) return;
        if (attributeSamplers.get(attribute) instanceof RingServerOrbital.LocalSampler sampler
                && sampler.ringworld$hasTimeLayer()) {
            cir.setReturnValue(sampler.ringworld$computeLocal(position, interpolator,
                    RingServerOrbital.offsetTicks(position.x)));
        }
    }

    @Inject(method = "getDimensionValue", at = @At("HEAD"), cancellable = true)
    private void ringworld$localDimensionValue(EnvironmentAttribute<?> attribute, CallbackInfoReturnable<Object> cir) {
        Vec3 context = RingServerOrbital.context();
        if (context == null || !RingServerOrbital.ownsSystem(this)) return;
        if (attributeSamplers.get(attribute) instanceof RingServerOrbital.LocalSampler sampler
                && sampler.ringworld$hasTimeLayer()) {
            cir.setReturnValue(sampler.ringworld$computeLocal(context, null,
                    RingServerOrbital.offsetTicks(context.x)));
        }
    }
}
