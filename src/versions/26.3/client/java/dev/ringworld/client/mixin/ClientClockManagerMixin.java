package dev.ringworld.client.mixin;

import dev.ringworld.client.RingLocalClock;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tags the overworld clock instance so only it reports local time; see {@link RingLocalClock}. */
@Mixin(ClientClockManager.class)
abstract class ClientClockManagerMixin {
    @Inject(method = "getInstance(Lnet/minecraft/core/Holder;)Lnet/minecraft/client/ClientClockManager$ClientClockInstance;",
            at = @At("RETURN"))
    private void ringworld$markOverworldClock(Holder<WorldClock> clock,
                                              CallbackInfoReturnable<ClientClockManager.ClientClockInstance> cir) {
        if (clock.is(WorldClocks.OVERWORLD) && cir.getReturnValue() instanceof RingLocalClock.Marked marked
                && !marked.ringworld$isOverworld()) {
            marked.ringworld$markOverworld();
        }
    }
}
