package dev.ringworld.mixin;

import dev.ringworld.server.RingServerOrbital;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.attribute.EnvironmentAttributeLayer;
import net.minecraft.world.attribute.SpatialAttributeInterpolator;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Orbital day: evaluates one attribute at a given local time (see {@link RingServerOrbital}).
 * The sampler and its time layers cache by tick id; a fresh scratch id forces this evaluation
 * to recompute, and leaves the caches on an id no real tick uses, so the next normal query
 * recomputes at world time.
 */
@Mixin(targets = "net.minecraft.world.attribute.EnvironmentAttributeSystem$ValueSampler")
abstract class OrbitalValueSamplerMixin implements RingServerOrbital.LocalSampler {
    @Unique private static final AtomicInteger RINGWORLD$SCRATCH_ID = new AtomicInteger(Integer.MIN_VALUE / 2);

    @Shadow @Final private List<EnvironmentAttributeLayer<?>> layers;
    @Shadow private int cacheTickId;
    @Shadow private Object computeValuePositional(Vec3 position, SpatialAttributeInterpolator interpolator) {
        throw new AssertionError();
    }

    @Unique private int ringworld$timeLayer = -1;

    @Override
    public boolean ringworld$hasTimeLayer() {
        if (ringworld$timeLayer < 0) {
            boolean any = false;
            for (Object layer : layers) any |= layer instanceof EnvironmentAttributeLayer.TimeBased;
            ringworld$timeLayer = any ? 1 : 0;
        }
        return ringworld$timeLayer == 1;
    }

    @Override
    public Object ringworld$computeLocal(Vec3 position, Object interpolator, long offsetTicks) {
        int saved = cacheTickId;
        int scratch = RINGWORLD$SCRATCH_ID.getAndDecrement();
        if (scratch > -2) RINGWORLD$SCRATCH_ID.set(Integer.MIN_VALUE / 2);
        cacheTickId = scratch;
        long previous = RingServerOrbital.pushClockOffset(offsetTicks);
        try {
            return computeValuePositional(position, (SpatialAttributeInterpolator) interpolator);
        } finally {
            RingServerOrbital.popClockOffset(previous);
            cacheTickId = saved;
        }
    }
}
