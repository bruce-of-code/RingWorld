package dev.ringworld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Either;
import dev.ringworld.server.RingServerOrbital;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Orbital day: players tick from their connection, not the level's entity loop, so give
 * their tick (bed-rule wake checks, etc.) and bed use their own longitude.
 */
@Mixin(ServerPlayer.class)
abstract class OrbitalServerPlayerMixin {
    @WrapMethod(method = "doTick")
    private void ringworld$playerContext(Operation<Void> original) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        if (!RingServerOrbital.activeFor(player.level())) {
            original.call();
            return;
        }
        Vec3 previous = RingServerOrbital.enter(player.position());
        try {
            original.call();
        } finally {
            RingServerOrbital.exit(previous);
        }
    }

    /** "You can only sleep at night" is judged by the bed's local time. */
    @WrapMethod(method = "startSleepInBed")
    private Either<Player.BedSleepingProblem, Unit> ringworld$bedContext(AbstractBedBlock block, BlockState state,
                                                                         BedRule rule, BlockPos pos,
                                                                         Operation<Either<Player.BedSleepingProblem, Unit>> original) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        if (!RingServerOrbital.activeFor(player.level())) return original.call(block, state, rule, pos);
        Vec3 previous = RingServerOrbital.enter(Vec3.atCenterOf(pos));
        try {
            return original.call(block, state, rule, pos);
        } finally {
            RingServerOrbital.exit(previous);
        }
    }
}
