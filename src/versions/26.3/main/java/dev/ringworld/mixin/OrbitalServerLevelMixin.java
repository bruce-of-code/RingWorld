package dev.ringworld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ringworld.server.RingServerOrbital;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Orbital day on the server Overworld: entity tick context and local-time sleeping. */
@Mixin(ServerLevel.class)
abstract class OrbitalServerLevelMixin {
    /** Picks up /ringworld daycycle changes (and the world itself) every Overworld tick. */
    @org.spongepowered.asm.mixin.injection.Inject(method = "tick", at = @At("HEAD"))
    private void ringworld$refreshOrbital(java.util.function.BooleanSupplier hasTimeLeft,
                                          org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        ServerLevel level = (ServerLevel)(Object)this;
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) RingServerOrbital.refresh(level);
    }

    /** Daylight queries made while an entity ticks use that entity's longitude. */
    @WrapMethod(method = "tickNonPassenger")
    private void ringworld$entityContext(Entity entity, Operation<Void> original) {
        if (!RingServerOrbital.activeFor((ServerLevel)(Object)this)) {
            original.call(entity);
            return;
        }
        Vec3 previous = RingServerOrbital.enter(entity.position());
        try {
            original.call(entity);
        } finally {
            RingServerOrbital.exit(previous);
        }
    }

    /** Only players in local night (or already asleep) count toward the sleep percentage. */
    @ModifyArg(method = "updateSleepingPlayerList", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/SleepStatus;update(Ljava/util/List;)Z"))
    private List<ServerPlayer> ringworld$sleepCandidates(List<ServerPlayer> players) {
        return RingServerOrbital.sleepCandidates((ServerLevel)(Object)this, players);
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"), index = 1)
    private List<ServerPlayer> ringworld$deepSleepCandidates(List<ServerPlayer> players) {
        return RingServerOrbital.sleepCandidates((ServerLevel)(Object)this, players);
    }

    /**
     * Vanilla skips to the next wake-up marker of the one world clock. On the orbital ring
     * that is someone else's morning; instead advance until every sleeper reaches local
     * dawn. Every other longitude moves forward by the same amount (vanilla's own rule,
     * applied to a clock whose reading depends on where you stand). The result is unused.
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/clock/ServerClockManager;moveToTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/clock/ServerClockManager$MoveResult;"))
    private ServerClockManager.MoveResult ringworld$wakeAtLocalDawn(ServerClockManager clocks, Holder<WorldClock> clock,
                                                                   ResourceKey<ClockTimeMarker> marker,
                                                                   Operation<ServerClockManager.MoveResult> original) {
        ServerLevel level = (ServerLevel)(Object)this;
        if (!RingServerOrbital.activeFor(level)) return original.call(clocks, clock, marker);
        int ticks = RingServerOrbital.ticksUntilSleepersWake(level, clocks.getInstance(clock).totalTicks());
        if (ticks > 0) clocks.addTicks(clock, ticks);
        return null;
    }
}
