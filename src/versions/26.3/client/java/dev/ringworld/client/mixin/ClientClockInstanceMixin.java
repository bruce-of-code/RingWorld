package dev.ringworld.client.mixin;

import dev.ringworld.client.RingLocalClock;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Orbital day: the overworld clock reports local time at the camera; see {@link RingLocalClock}. */
@Mixin(ClientClockManager.ClientClockInstance.class)
abstract class ClientClockInstanceMixin implements RingLocalClock.Marked {
    @Shadow private long totalTicks;
    @Unique private boolean ringworld$overworld;

    @Override public void ringworld$markOverworld() { ringworld$overworld = true; }
    @Override public boolean ringworld$isOverworld() { return ringworld$overworld; }
    @Override public long ringworld$rawTotalTicks() { return totalTicks; }

    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
    private void ringworld$localTime(CallbackInfoReturnable<Long> cir) {
        if (ringworld$overworld) cir.setReturnValue(RingLocalClock.adjust(cir.getReturnValueJ()));
    }
}
