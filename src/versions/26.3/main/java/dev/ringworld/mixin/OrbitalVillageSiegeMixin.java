package dev.ringworld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ringworld.server.RingServerOrbital;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.ai.village.VillageSiege;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Orbital day: vanilla rolls a zombie siege once, at the central clock's siege marker
 * (around midnight), cancels it whenever the central sky is bright, and then picks any player
 * standing in a village. Here the roll fires when a player's LOCAL time reaches the marker,
 * the siege is cancelled only once every player is in local daylight, and only players in
 * local night can host it.
 */
@Mixin(VillageSiege.class)
abstract class OrbitalVillageSiegeMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;isBrightOutside()Z"))
    private boolean ringworld$brightForEveryone(ServerLevel level, Operation<Boolean> original) {
        if (!RingServerOrbital.activeFor(level)) return original.call(level);
        return RingServerOrbital.brightForAllPlayers(level);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/clock/ServerClockManager;isAtTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Z"))
    private boolean ringworld$localSiegeMarker(ServerClockManager clocks, Holder<WorldClock> clock,
                                               ResourceKey<ClockTimeMarker> marker, Operation<Boolean> original,
                                               ServerLevel level) {
        if (!RingServerOrbital.activeFor(level)) return original.call(clocks, clock, marker);
        ServerClockManager.ServerClockInstance instance = clocks.getInstance(clock);
        ClockTimeMarker definition = ((OrbitalClockInstanceAccessor)(Object)instance).ringworld$timeMarkers().get(marker);
        if (definition == null) return false;
        long raw = instance.totalTicks();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            if (definition.occursAt(RingServerOrbital.localTicks(raw, player.getX()))) return true;
        }
        return false;
    }

    @WrapOperation(method = "tryToSetupSiege", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;"))
    private List<ServerPlayer> ringworld$playersInLocalNight(ServerLevel level, Operation<List<ServerPlayer>> original) {
        List<ServerPlayer> players = original.call(level);
        if (!RingServerOrbital.activeFor(level)) return players;
        return players.stream().filter(player -> !RingServerOrbital.brightAt(level, player.getX())).toList();
    }
}
