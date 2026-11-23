package dev.ringworld.mixin;

import dev.ringworld.server.RingServerOrbital;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Orbital day: per-longitude sky darkness on the RingWorld Overworld server.
 * {@code getSkyDarken}/{@code isBrightOutside} use the position being ticked (AI goals, mob
 * burning, bed rules, phantoms...); spawn-light checks use the spawn position. Without a
 * context or outside the orbital Overworld everything is vanilla.
 */
@Mixin(Level.class)
abstract class OrbitalLevelDaylightMixin implements LevelReader {
    @Inject(method = "getSkyDarken", at = @At("HEAD"), cancellable = true)
    private void ringworld$localSkyDarken(CallbackInfoReturnable<Integer> cir) {
        Vec3 context = RingServerOrbital.context();
        if (context != null && RingServerOrbital.activeFor((Level)(Object)this)) {
            cir.setReturnValue(RingServerOrbital.skyDarkenAt((Level)(Object)this, context.x));
        }
    }

    @Inject(method = "isBrightOutside", at = @At("HEAD"), cancellable = true)
    private void ringworld$localBrightOutside(CallbackInfoReturnable<Boolean> cir) {
        Vec3 context = RingServerOrbital.context();
        if (context != null && RingServerOrbital.activeFor((Level)(Object)this)) {
            cir.setReturnValue(RingServerOrbital.brightAt((Level)(Object)this, context.x));
        }
    }

    /** Overrides LevelReader's default (global darkness) with the darkness at {@code pos}. */
    @Override
    public int getMaxLocalRawBrightness(BlockPos pos) {
        Level level = (Level)(Object)this;
        int darken = RingServerOrbital.activeFor(level)
                ? RingServerOrbital.skyDarkenAt(level, pos.getX() + 0.5) : level.getSkyDarken();
        return getMaxLocalRawBrightness(pos, darken);
    }
}
