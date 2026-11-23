package dev.ringworld.world;

import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;

/** Implemented by the vanilla noise generator mixin once it belongs to a ring Overworld. */
public interface RingWorldGeneratorAccess {
    void ringworld$setGeometry(@org.jetbrains.annotations.Nullable RingGeometry geometry);
    RingGeometry ringworld$getGeometry();
    void ringworld$setTerrainNoiseMapping(int mappingVersion);
    int ringworld$getTerrainNoiseMapping();
    void ringworld$setWallHeight(int wallHeightBlocks);
    int ringworld$getWallHeight();
    void ringworld$setWallStyle(RingWallStyle wallStyle);
    RingWallStyle ringworld$getWallStyle();
    void ringworld$setGuaranteeStronghold(boolean guaranteeStronghold);
    boolean ringworld$guaranteesStronghold();
    void ringworld$setGenerationSettings(RingWorldGenerationSettings settings, long generatorSeed);
    RingWorldGenerationSettings ringworld$getGenerationSettings();
    Climate.Sampler ringworld$getPeriodicClimateSampler(RandomState randomState);

    /**
     * River macro terrain for this generator, with its terrain-aware course built first if it
     * isn't yet (experiment, 2026-11-22). Call only where no NoiseChunk is live on this thread;
     * see {@link RingRiverCourse}. Returns null when the world has no continuous river.
     */
    default @org.jetbrains.annotations.Nullable RingMacroTerrain ringworld$riverTerrain(
            net.minecraft.world.level.LevelHeightAccessor heights, RandomState randomState) {
        return null;
    }
}
