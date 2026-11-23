package dev.ringworld.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Immutable optional world-generation choices selected before first load. */
public record RingWorldGenerationSettings(
        RingAtlasFidelity atlasFidelity,
        RingWorldLayout layout,
        boolean continuousRiver,
        RingStructureAbundance structureAbundance,
        int formatVersion) {
    public static final int FORMAT_VERSION = 1;
    public static final RingWorldGenerationSettings DEFAULT = new RingWorldGenerationSettings(
            RingAtlasFidelity.BALANCED, RingWorldLayout.VANILLA, false, RingStructureAbundance.VANILLA,
            FORMAT_VERSION);
    public static final Codec<RingWorldGenerationSettings> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    RingAtlasFidelity.CODEC.fieldOf("atlas_fidelity")
                            .forGetter(RingWorldGenerationSettings::atlasFidelity),
                    RingWorldLayout.CODEC.fieldOf("layout")
                            .forGetter(RingWorldGenerationSettings::layout),
                    Codec.BOOL.fieldOf("continuous_river")
                            .forGetter(RingWorldGenerationSettings::continuousRiver),
                    // Legacy boolean kept (written as "any extra") so an older jar can still
                    // read the save; structure_abundance wins when present. Pre-2026-11-22
                    // saves only have the boolean, and "on" reads as 2x.
                    Codec.BOOL.optionalFieldOf("more_structures", false)
                            .forGetter(RingWorldGenerationSettings::moreStructures),
                    RingStructureAbundance.CODEC.optionalFieldOf("structure_abundance")
                            .forGetter(settings -> java.util.Optional.of(settings.structureAbundance())),
                    Codec.INT.fieldOf("format")
                            .forGetter(RingWorldGenerationSettings::formatVersion)
            ).apply(instance, (fidelity, layout, river, legacyMore, abundance, format) ->
                    new RingWorldGenerationSettings(fidelity, layout, river,
                            abundance.orElse(legacyMore ? RingStructureAbundance.DOUBLE
                                    : RingStructureAbundance.VANILLA),
                            format)));

    public RingWorldGenerationSettings {
        if (atlasFidelity == null) throw new IllegalArgumentException("Atlas fidelity is required");
        if (layout == null) throw new IllegalArgumentException("world layout is required");
        if (structureAbundance == null) structureAbundance = RingStructureAbundance.VANILLA;
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported RingWorld generation-settings format " + formatVersion);
        }
    }

    /** Legacy on/off form used by fixtures: on means {@link RingStructureAbundance#DOUBLE}. */
    public RingWorldGenerationSettings(RingAtlasFidelity atlasFidelity, RingWorldLayout layout,
                                       boolean continuousRiver, boolean moreStructures, int formatVersion) {
        this(atlasFidelity, layout, continuousRiver,
                moreStructures ? RingStructureAbundance.DOUBLE : RingStructureAbundance.VANILLA,
                formatVersion);
    }

    public RingWorldGenerationSettings withAtlasFidelity(RingAtlasFidelity fidelity) {
        return new RingWorldGenerationSettings(
                fidelity, layout, continuousRiver, structureAbundance, FORMAT_VERSION);
    }

    public RingWorldGenerationSettings withLayout(RingWorldLayout replacement) {
        return new RingWorldGenerationSettings(
                atlasFidelity, replacement, continuousRiver, structureAbundance, FORMAT_VERSION);
    }

    public RingWorldGenerationSettings withContinuousRiver(boolean enabled) {
        return new RingWorldGenerationSettings(
                atlasFidelity, layout, enabled, structureAbundance, FORMAT_VERSION);
    }

    /** Legacy on/off: on means {@link RingStructureAbundance#DOUBLE}. */
    public RingWorldGenerationSettings withMoreStructures(boolean enabled) {
        return withStructureAbundance(enabled ? RingStructureAbundance.DOUBLE : RingStructureAbundance.VANILLA);
    }

    public RingWorldGenerationSettings withStructureAbundance(RingStructureAbundance abundance) {
        return new RingWorldGenerationSettings(
                atlasFidelity, layout, continuousRiver, abundance, FORMAT_VERSION);
    }

    /** True for any level above vanilla. */
    public boolean moreStructures() {
        return structureAbundance != RingStructureAbundance.VANILLA;
    }
}
