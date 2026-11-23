package dev.ringworld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ringworld.server.RingServerOrbital;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Orbital day: vanilla skips patrols while the (one, central) sky is bright, then picks a
 * player and spawns 24-48 blocks away. Skip only when every player is in local daylight, and
 * require local night at the chosen spawn spot.
 */
@Mixin(PatrolSpawner.class)
abstract class OrbitalPatrolSpawnerMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;isBrightOutside()Z"))
    private boolean ringworld$brightForEveryone(ServerLevel level, Operation<Boolean> original) {
        if (!RingServerOrbital.activeFor(level)) return original.call(level);
        return RingServerOrbital.brightForAllPlayers(level);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/EnvironmentAttributeSystem;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/core/BlockPos;)Ljava/lang/Object;"))
    private Object ringworld$localNightAtSpawn(EnvironmentAttributeSystem system, EnvironmentAttribute<?> attribute,
                                               BlockPos pos, Operation<Object> original, ServerLevel level) {
        Object value = original.call(system, attribute, pos);
        if (!RingServerOrbital.activeFor(level) || !(value instanceof Boolean allowed)) return value;
        return allowed && !RingServerOrbital.brightAt(level, pos.getX() + 0.5);
    }
}
