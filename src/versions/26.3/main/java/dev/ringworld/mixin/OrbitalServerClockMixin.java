package dev.ringworld.mixin;

import dev.ringworld.server.RingServerOrbital;
import dev.ringworld.world.RingOrbitalDay;
import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Orbital day: during one local attribute evaluation (and only then, only on that thread)
 * the server clock reports local time. The clock's own tick/save/sync read the raw field.
 */
@Mixin(ServerClockManager.ServerClockInstance.class)
abstract class OrbitalServerClockMixin {
    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
    private void ringworld$localTime(CallbackInfoReturnable<Long> cir) {
        long offset = RingServerOrbital.clockOffset();
        if (offset < 0) return;
        long local = cir.getReturnValueJ() - offset;
        cir.setReturnValue(local >= 0 ? local : local + RingOrbitalDay.DAY_TICKS);
    }
}
