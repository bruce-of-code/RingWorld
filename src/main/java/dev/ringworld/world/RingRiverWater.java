package dev.ringworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Makes the continuous river's channel exactly what the noise router carved: water from the
 * bed up to sea level, air above. Runs after terrain, surface rules and carvers
 * (experiment branch, 2026-11-22).
 *
 * <p>Why: the router carves the channel through density only ({@code FINAL_DENSITY} is forced
 * negative from {@link RingMacroTerrain#riverChannelFloor} upward), and vanilla's aquifer then
 * decides what fills each carved cell. Inside the channel that produced three artifacts seen
 * in game:
 * <ul>
 *   <li>dry stretches, where an aquifer region chose a level below sea level, or air;</li>
 *   <li>one-block walls crossing the river in S-curves just under the surface: the aquifer's
 *       barrier between two regions with different fluid levels (once the dry side was
 *       filled, the wall was left standing in open water);</li>
 *   <li>boxes of water standing above the river in canyons: an aquifer region whose level
 *       sits above sea level, held in by its barrier walls.</li>
 * </ul>
 * Every carved cell is non-solid by construction, so any block found there is aquifer output
 * and is replaced. Below the bed, a carver opening gets at most {@link #MAX_PLUG} blocks of
 * water so a cave under the river becomes a short plug, not a flooded shaft.
 *
 * <p>Edges: new water next to air, and existing water next to new air, are marked for
 * post-processing so they flow when the chunk loads rather than standing as static walls.
 * The caller passes the chunk's mark method because its name differs by version
 * ({@code markPosForPostprocessing} on 26.1, {@code markPosForPostProcessing} on 26.3).
 */
public final class RingRiverWater {
    /** Water allowed below the carved bed, into carver openings. */
    private static final int MAX_PLUG = 2;
    private static final int[][] NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};

    private RingRiverWater() { }

    public static void fill(ChunkAccess chunk, RingMacroTerrain macro, int seaLevel,
                            Consumer<BlockPos> markForPostProcessing) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<BlockPos> changed = new ArrayList<>();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = minX + lx, z = minZ + lz;
                double river = macro.riverInfluence(x, z);
                if (river <= 0.0) continue;
                int floor = (int)Math.ceil(RingMacroTerrain.riverChannelFloor(river, seaLevel));
                int top = Math.max(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz) - 1, seaLevel - 1);
                // Carved channel: top-down so heightmap updates only ever step downward.
                for (int y = top; y >= floor; y--) {
                    BlockState want = y < seaLevel ? water : air;
                    pos.set(x, y, z);
                    if (chunk.getBlockState(pos) == want) continue;
                    chunk.setBlockState(pos, want);
                    worldSurface.update(lx, y, lz, want);
                    oceanFloor.update(lx, y, lz, want);
                    changed.add(pos.immutable());
                }
                // Short plug into a carver opening under the bed, only beneath water.
                if (floor > seaLevel) continue;
                for (int y = floor - 1; y >= floor - MAX_PLUG; y--) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.is(Blocks.WATER)) continue;
                    if (!state.isAir()) break;
                    chunk.setBlockState(pos, water);
                    worldSurface.update(lx, y, lz, water);
                    changed.add(pos.immutable());
                }
            }
        }
        for (BlockPos cell : changed) {
            boolean isWater = chunk.getBlockState(cell).is(Blocks.WATER);
            for (int[] o : NEIGHBOURS) {
                int lx = cell.getX() - minX + o[0], lz = cell.getZ() - minZ + o[2];
                if (lx < 0 || lx > 15 || lz < 0 || lz > 15) continue;
                pos.set(cell.getX() + o[0], cell.getY() + o[1], cell.getZ() + o[2]);
                BlockState neighbour = chunk.getBlockState(pos);
                if (isWater && neighbour.isAir()) {
                    markForPostProcessing.accept(cell);
                    break;
                }
                if (!isWater && neighbour.is(Blocks.WATER)) markForPostProcessing.accept(pos.immutable());
            }
        }
    }
}
