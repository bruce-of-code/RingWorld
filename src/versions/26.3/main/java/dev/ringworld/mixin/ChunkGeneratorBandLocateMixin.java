package dev.ringworld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingGeometry;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Explorer / buried-treasure maps and /locate: vanilla's random-spread search walks candidate
 * chunks on an unbounded plane, and the exterior void chunks beyond the rims still sample real
 * biomes, so a structure start "exists" there although nothing is built. Skip any candidate
 * whose locate position is not inside the band between the rims, so the search keeps going to
 * the nearest one actually on the ring.
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorBandLocateMixin {
    @WrapOperation(method = "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/StructureManager;IIIZJLnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;)Lcom/mojang/datafixers/util/Pair;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getStructureGeneratingAt(Ljava/util/Set;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/StructureManager;ZLnet/minecraft/world/level/levelgen/structure/placement/StructurePlacement;Lnet/minecraft/world/level/ChunkPos;)Lcom/mojang/datafixers/util/Pair;"))
    private static Pair<BlockPos, Holder<Structure>> ringworld$onlyOnTheBand(
            Set<Holder<Structure>> structures, LevelReader level, StructureManager manager, boolean skipKnown,
            StructurePlacement placement, ChunkPos chunk, Operation<Pair<BlockPos, Holder<Structure>>> original) {
        if (level instanceof ServerLevel server && server.dimension() == Level.OVERWORLD) {
            RingGeometry geometry = RingWorldServer.geometryFor(server);
            int z = placement.getLocatePos(chunk).getZ();
            if (z < geometry.minWidthZ() + RingGenerationBoundary.RIM_THICKNESS
                    || z > geometry.maxWidthZ() - RingGenerationBoundary.RIM_THICKNESS) return null;
        }
        return original.call(structures, level, manager, skipKnown, placement, chunk);
    }
}
