package dev.ringworld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ringworld.server.RingServerOrbital;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Orbital day: vanilla gates phantoms on global darkness (>= 5) before looping players. Gate
 * on the darkest player's local sky instead, and only consider players whose own longitude
 * is dark enough.
 */
@Mixin(PhantomSpawner.class)
abstract class OrbitalPhantomSpawnerMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getSkyDarken()I"))
    private int ringworld$darkestPlayer(ServerLevel level, Operation<Integer> original) {
        if (!RingServerOrbital.activeFor(level)) return original.call(level);
        int darkest = 0;
        for (ServerPlayer player : level.players()) {
            darkest = Math.max(darkest, RingServerOrbital.skyDarkenAt(level, player.getX()));
        }
        return darkest;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;"))
    private List<ServerPlayer> ringworld$playersInLocalNight(ServerLevel level, Operation<List<ServerPlayer>> original) {
        List<ServerPlayer> players = original.call(level);
        if (!RingServerOrbital.activeFor(level)) return players;
        return players.stream().filter(player -> RingServerOrbital.skyDarkenAt(level, player.getX()) >= 5).toList();
    }
}
