package dev.ringworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.function.Consumer;

/**
 * Fills the continuous river's open channel with water after terrain and carvers
 * (experiment branch, 2026-11-22).
 *
 * <p>Why: the river is carved purely through density ({@code RingNoiseRouter} forces the
 * channel to air from the bed upward). Whether that air below sea level becomes water is
 * then left to vanilla's aquifer, which near the surface may pick a LOCAL fluid level below
 * sea level, or air, for a whole aquifer cell. Carvers that cross the channel re-ask the
 * aquifer the same way. Result: dry, sometimes chunk-shaped, stretches of riverbed.
 *
 * <p>Rule, per column inside the river corridor ({@code riverInfluence > 0}):
 * <ul>
 *   <li>only if the column is open to the sky at sea level (WORLD_SURFACE_WG top at or below
 *       sea level), so caves under an overhanging bank are never flooded;</li>
 *   <li>walk down from {@code seaLevel - 1}, turning air into water source and passing
 *       through existing water, and stop at the first other block or at the deepest
 *       possible bed ({@link #MAX_DEPTH} below sea level). A carved cave opening in the bed
 *       therefore gets at most a short plug of water, not a flooded shaft.</li>
 * </ul>
 * A new water cell next to air in the same chunk is marked for post-processing so it starts
 * flowing when the chunk loads instead of standing as a static wall. The caller passes the
 * chunk's mark method because its name differs by version ({@code markPosForPostprocessing}
 * on 26.1, {@code markPosForPostProcessing} on 26.3).
 */
public final class RingRiverWater {
    /** The router's bed is at most {@code seaLevel - 7}; one extra block of margin. */
    private static final int MAX_DEPTH = 8;

    private RingRiverWater() { }

    public static void fill(ChunkAccess chunk, RingMacroTerrain macro, int seaLevel,
                            Consumer<BlockPos> markForPostProcessing) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockState water = Blocks.WATER.defaultBlockState();
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean[] filled = new boolean[16 * 16 * MAX_DEPTH];
        boolean any = false;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                if (macro.riverInfluence(minX + lx, minZ + lz) <= 0.0) continue;
                if (chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz) > seaLevel) continue;
                for (int d = 0; d < MAX_DEPTH; d++) {
                    int y = seaLevel - 1 - d;
                    pos.set(minX + lx, y, minZ + lz);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.is(Blocks.WATER)) continue;
                    if (!state.isAir()) break;
                    chunk.setBlockState(pos, water);
                    worldSurface.update(lx, y, lz, water);
                    filled[(d * 16 + lz) * 16 + lx] = true;
                    any = true;
                }
            }
        }
        if (!any) return;
        for (int d = 0; d < MAX_DEPTH; d++) {
            int y = seaLevel - 1 - d;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    if (!filled[(d * 16 + lz) * 16 + lx]) continue;
                    if (touchesAir(chunk, pos, minX, minZ, lx, y, lz)) {
                        markForPostProcessing.accept(pos.set(minX + lx, y, minZ + lz).immutable());
                    }
                }
            }
        }
    }

    private static boolean touchesAir(ChunkAccess chunk, BlockPos.MutableBlockPos pos,
                                      int minX, int minZ, int lx, int y, int lz) {
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
        for (int[] o : offsets) {
            int nx = lx + o[0], nz = lz + o[2];
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
            if (chunk.getBlockState(pos.set(minX + nx, y + o[1], minZ + nz)).isAir()) return true;
        }
        return false;
    }
}
